<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import PowWorker from '../../workers/pow.worker.js?worker'
import { createHasher } from '../../utils/sha256.js'

/**
 * 一键人机验证。
 *
 * 交互上只有一个复选框：用户点一下即可。点击之后组件做三件事——
 *  1. 在 Worker 里算出服务端下发的工作量证明（难度 19 大约 50 万次哈希）；
 *  2. 等满服务端要求的随机等待时长（challenge.human.minDurationMs，1~3 秒随机）再调校验；
 *  3. 把这一路上的指针 / 触摸 / 键盘事件汇总成行为证据一起提交。
 *
 * 防自动化设计：
 *  - 所有 class 名每次挂载随机生成（样式运行时注入），自动化脚本无法按固定选择器定位；
 *  - 复选框在卡片内的排布位置每次随机（左 / 中 / 右 + 随机留白 + 品牌标随机隐藏）；
 *  - PoW 在面板打开时静默预热，等待期对用户表现为「安全校验中」的进度动画。
 */
const props = defineProps({
  /** 服务端下发的 challenge（含 human 字段） */
  challenge: { type: Object, required: true },
  /** 父级正在校验时置 true */
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['complete'])

// ---------------------------------------------------------------- 随机类名与注入样式

const ns = `sc${Math.random().toString(36).slice(2, 10)}`
const C = {
  root: `${ns}-r`,
  box: `${ns}-bx`,
  check: `${ns}-ck`,
  spin: `${ns}-sp`,
  body: `${ns}-bd`,
  title: `${ns}-tt`,
  sub: `${ns}-sb`,
  brand: `${ns}-br`,
  bar: `${ns}-pb`,
  barFill: `${ns}-pf`,
}
/** 状态修饰类也随机化后缀，避免出现稳定的 is-done / is-error 选择器 */
const stateSuffix = {
  idle: `i${Math.floor(Math.random() * 1e6).toString(36)}`,
  working: `w${Math.floor(Math.random() * 1e6).toString(36)}`,
  done: `d${Math.floor(Math.random() * 1e6).toString(36)}`,
  error: `e${Math.floor(Math.random() * 1e6).toString(36)}`,
}

let styleEl = null
function injectStyle() {
  if (typeof document === 'undefined' || styleEl) return
  const css = `
.${C.root}{width:100%}
.${C.box}{width:100%;min-height:76px;display:flex;align-items:center;gap:12px;
  border:1px solid var(--bc-surface-border,#e2e8f0);border-radius:10px;
  background:var(--bc-surface,#fff);text-align:left;
  transition:border-color .18s,background .18s,box-shadow .18s}
.${C.box}.st-${stateSuffix.working},.${C.box}.st-${stateSuffix.done},.${C.box}.st-${stateSuffix.error}{cursor:default}
.${C.box}.st-${stateSuffix.done}{border-color:var(--bc-ok-border,#bbf7d0);background:var(--bc-ok-bg,#f0fdf4)}
.${C.box}.st-${stateSuffix.error}{border-color:var(--bc-danger,#dc2626)}
.${C.check}{flex:none;width:26px;height:26px;display:flex;align-items:center;justify-content:center;
  padding:0;border:2px solid var(--bc-track-border,#e2e8f0);border-radius:7px;
  background:var(--bc-track-bg,#eef2f7);color:#fff;cursor:pointer;transition:all .2s}
.${C.check}:hover:not(:disabled){border-color:var(--bc-primary,#4f46e5)}
.${C.check}:focus-visible{outline:none;box-shadow:0 0 0 3px var(--bc-primary-ring,rgba(79,70,229,.15))}
.${C.check}:disabled{cursor:default}
.${C.box}.st-${stateSuffix.done} .${C.check}{border-color:var(--bc-success,#16a34a);background:var(--bc-success,#16a34a)}
.${C.box}.st-${stateSuffix.error} .${C.check}{border-color:var(--bc-danger,#dc2626)}
.${C.spin}{width:15px;height:15px;border-radius:50%;border:2px solid var(--bc-primary-weak,#c7d2fe);
  border-top-color:var(--bc-primary,#4f46e5);animation:${ns}-spin .75s linear infinite}
@keyframes ${ns}-spin{to{transform:rotate(360deg)}}
.${C.body}{flex:1;min-width:0;display:flex;flex-direction:column;gap:3px}
.${C.title}{font-size:14.5px;font-weight:600;color:var(--bc-text,#0f172a);line-height:1.2}
.${C.box}.st-${stateSuffix.done} .${C.title}{color:var(--bc-ok-text,#15803d)}
.${C.box}.st-${stateSuffix.error} .${C.title}{color:var(--bc-danger,#dc2626)}
.${C.sub}{font-size:12px;color:var(--bc-text-faint,#94a3b8);line-height:1.25;
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.${C.brand}{flex:none;display:flex;flex-direction:column;align-items:center;gap:3px;
  color:var(--bc-primary,#4f46e5);opacity:.85}
.${C.brand} em{font-style:normal;font-size:9px;letter-spacing:.02em;
  color:var(--bc-text-faint,#94a3b8);transform:scale(.92)}
.${C.bar}{margin-top:8px;height:3px;border-radius:2px;background:var(--bc-track-bg,#eef2f7);overflow:hidden}
.${C.barFill}{display:block;height:100%;border-radius:2px;
  background:linear-gradient(90deg,var(--bc-primary-mid,#818cf8),var(--bc-primary,#4f46e5));
  transition:width .18s linear}
`
  styleEl = document.createElement('style')
  styleEl.setAttribute('data-sc', ns)
  styleEl.textContent = css
  document.head.appendChild(styleEl)
}

// ---------------------------------------------------------------- 复选框随机位置

/** 每次挂载随机决定复选框在卡片中的排布：左 / 中 / 右 + 随机左右留白 + 品牌标随机隐藏 */
const layout = (() => {
  const variant = Math.floor(Math.random() * 3)
  const padL = 12 + Math.floor(Math.random() * 5) * 8 // 12 ~ 44px
  const padR = 12 + Math.floor(Math.random() * 5) * 8
  const orders =
    variant === 0
      ? { check: 0, body: 1, brand: 2 } // 左（默认位）
      : variant === 1
        ? { body: 0, check: 1, brand: 2 } // 中
        : { brand: 0, body: 1, check: 2 } // 右
  return {
    boxStyle: { paddingLeft: `${padL}px`, paddingRight: `${padR}px` },
    checkStyle: { order: orders.check },
    bodyStyle: { order: orders.body },
    brandStyle: { order: orders.brand },
    showBrand: Math.random() < 0.75,
  }
})()

// ---------------------------------------------------------------- 状态与文案

const state = ref('idle') // idle | working | done | error
const message = ref('')
const progress = ref(0)

const human = computed(() => props.challenge?.human || null)
const difficulty = computed(() => Math.max(0, Number(human.value?.difficulty ?? 0)))
const expectedTries = computed(() => Math.pow(2, difficulty.value))
/** 服务端要求的等待时长（每张验证码随机 1~3 秒，与后端判定同源） */
const requiredDelayMs = computed(() => Math.max(0, Number(human.value?.minDurationMs ?? 0)))

const title = computed(() => {
  if (state.value === 'done') return '验证通过'
  if (state.value === 'working') return '正在校验环境'
  if (state.value === 'error') return '点击重试'
  return '我不是机器人'
})

const subtitle = computed(() => {
  if (state.value === 'done') return '正在提交结果…'
  if (state.value === 'working') return `安全校验中 ${Math.round(progress.value * 100)}%`
  if (state.value === 'error') return message.value || '校验失败，请重试'
  return '点击复选框完成验证'
})

const boxClass = computed(() => [C.box, `st-${stateSuffix[state.value] ?? stateSuffix.idle}`])
const boxStyle = computed(() => ({ ...layout.boxStyle }))

// ---------------------------------------------------------------- 行为证据采集

const stats = {
  moves: 0,
  clicks: 0,
  keys: 0,
  distance: 0,
  x: 0,
  y: 0,
  firstX: 0,
  firstY: 0,
  pointer: '',
}

function onPointerMove(e) {
  const x = e.clientX
  const y = e.clientY
  if (stats.moves === 0) {
    stats.firstX = x
    stats.firstY = y
  } else {
    stats.distance += Math.hypot(x - stats.x, y - stats.y)
  }
  stats.x = x
  stats.y = y
  stats.moves += 1
}

function onPointerDown(e) {
  stats.clicks += 1
  stats.pointer = e.pointerType || 'mouse'
  if (stats.moves === 0) {
    stats.firstX = e.clientX
    stats.firstY = e.clientY
    stats.x = e.clientX
    stats.y = e.clientY
  }
}

function onKeyDown() {
  stats.keys += 1
}

/** 汇总成服务端要的结构（字段名与 HumanTrace 一致） */
function snapshot() {
  const displacement = stats.moves > 1 ? Math.hypot(stats.x - stats.firstX, stats.y - stats.firstY) : 0
  const straight = stats.distance > 0 ? Math.min(1, displacement / stats.distance) : 0
  return {
    moves: stats.moves,
    clicks: stats.clicks,
    keys: stats.keys,
    distance: Math.round(stats.distance),
    straight: Number(straight.toFixed(4)),
    pointer: stats.pointer || (stats.moves > 0 ? 'mouse' : ''),
  }
}

// ---------------------------------------------------------------- 工作量证明

let destroyed = false
let prefetch = null
let activeWorker = null

function hashPrefix() {
  const h = human.value
  if (!h) return ''
  // 必须与服务端 ProofOfWork.prefix 完全一致：domain|token|salt|
  return `${h.domain || 'SC1'}|${props.challenge.token}|${h.salt}|`
}

function randomStart() {
  return (Math.random() * 0xffff) >>> 0
}

/** 主线程分片兜底（Worker 不可用时）：每 2 万次让出一次事件循环 */
async function solveOnMainThread(prefix, diff) {
  const hasher = createHasher(prefix)
  const base = randomStart()
  const budget = Math.min(1 << 24, expectedTries.value * 24)
  let offset = 0
  while (offset < budget) {
    const result = hasher.solve(diff, (base + offset) >>> 0, 20000)
    if (result) return result
    offset += 20000
    if (!destroyed) progress.value = Math.min(0.6, 0.6 * (1 - Math.exp(-offset / expectedTries.value)))
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
  throw new Error('校验超时，请重试')
}

function solve(prefix, diff) {
  if (diff <= 0) {
    return Promise.resolve({ nonce: 0 })
  }
  let worker
  try {
    worker = new PowWorker()
  } catch (e) {
    worker = null
  }
  if (!worker) {
    return solveOnMainThread(prefix, diff)
  }

  activeWorker = worker
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup()
      reject(new Error('校验超时，请重试'))
    }, 20000)

    function cleanup() {
      clearTimeout(timer)
      if (activeWorker === worker) activeWorker = null
      try {
        worker.terminate()
      } catch (e) {
        /* 已终止 */
      }
    }

    worker.onmessage = (event) => {
      const data = event.data || {}
      if (data.type === 'progress') {
        progress.value = Math.min(0.6, 0.6 * (1 - Math.exp(-data.tried / expectedTries.value)))
      } else if (data.type === 'done') {
        cleanup()
        resolve({ nonce: data.nonce })
      } else if (data.type === 'exhausted') {
        cleanup()
        reject(new Error('校验失败，请重试'))
      } else if (data.type === 'error') {
        cleanup()
        reject(new Error(data.message || '校验失败'))
      }
    }
    worker.onerror = () => {
      cleanup()
      reject(new Error('校验线程异常，请重试'))
    }
    worker.postMessage({
      prefix,
      difficulty: diff,
      start: randomStart(),
      budget: Math.min(1 << 24, expectedTries.value * 24),
    })
  })
}

function fail(text) {
  state.value = 'error'
  message.value = text
  progress.value = 0
}

// ---------------------------------------------------------------- 随机等待

/** 面板就绪时刻：服务端要求的等待时长从这个点起算（与父级 duration 计时同源） */
const readyAt = typeof performance !== 'undefined' ? performance.now() : Date.now()

/**
 * 等到「要求等待时长 + 随机抖动」为止。
 * 若用户点击前已经过了足够时间（比如先读了会儿提示），则只补一个很短的抖动。
 */
async function waitRequiredDelay() {
  const jitter = 200 + Math.random() * 600
  const target = readyAt + requiredDelayMs.value + jitter
  const now = typeof performance !== 'undefined' ? performance.now() : Date.now()
  const remain = target - now
  if (remain <= 0) return
  const total = remain
  const deadline = now + total
  while (!destroyed) {
    await new Promise((resolve) => setTimeout(resolve, Math.min(80, Math.max(1, deadline - (typeof performance !== 'undefined' ? performance.now() : Date.now())))))
    const cur = typeof performance !== 'undefined' ? performance.now() : Date.now()
    const timeProgress = Math.min(1, (cur - (deadline - total)) / total)
    progress.value = 0.6 + 0.4 * timeProgress
    if (cur >= deadline) break
  }
}

async function start() {
  if (state.value === 'working' || state.value === 'done' || props.disabled) return
  if (!human.value) {
    fail('验证参数缺失，请刷新')
    return
  }

  state.value = 'working'
  message.value = ''
  progress.value = 0
  try {
    prefetch = prefetch || solve(hashPrefix(), difficulty.value)
    const result = await prefetch
    if (destroyed) return
    // 工作量证明完成后，等满服务端要求的随机时长再调校验接口
    await waitRequiredDelay()
    if (destroyed) return
    state.value = 'done'
    progress.value = 1
    emit('complete', { nonce: result.nonce, trace: snapshot() })
  } catch (e) {
    prefetch = null
    fail(e?.message || '校验失败，请重试')
  }
}

onMounted(() => {
  injectStyle()
  if (typeof window === 'undefined') return
  window.addEventListener('pointermove', onPointerMove, { passive: true })
  window.addEventListener('pointerdown', onPointerDown, { passive: true, capture: true })
  window.addEventListener('keydown', onKeyDown, { passive: true })
  // 面板一打开就开始算，用户点击时结果通常已经就绪
  if (human.value && difficulty.value > 0) {
    prefetch = solve(hashPrefix(), difficulty.value)
    prefetch.catch(() => {
      prefetch = null
    })
  }
})

onBeforeUnmount(() => {
  destroyed = true
  if (styleEl) {
    styleEl.remove()
    styleEl = null
  }
  if (typeof window !== 'undefined') {
    window.removeEventListener('pointermove', onPointerMove)
    window.removeEventListener('pointerdown', onPointerDown, { capture: true })
    window.removeEventListener('keydown', onKeyDown)
  }
  if (activeWorker) {
    try {
      activeWorker.terminate()
    } catch (e) {
      /* 忽略 */
    }
    activeWorker = null
  }
})
</script>

<template>
  <div :class="C.root">
    <!-- 注意：只有复选框方块本身可点。卡片其余区域（文案、品牌标）点击不触发验证。
         类名与位置每次挂载随机，防止自动化脚本按固定选择器 / 坐标触发点击。 -->
    <div :class="boxClass" :style="boxStyle">
      <button
        type="button"
        :class="C.check"
        :style="layout.checkStyle"
        role="checkbox"
        :aria-checked="state === 'done' ? 'true' : 'false'"
        :aria-label="title"
        :disabled="disabled || state === 'working'"
        @click="start"
      >
        <svg v-if="state === 'done'" viewBox="0 0 24 24" width="16" height="16">
          <path
            d="m5 12.5 4.5 4.5L19 7.5"
            fill="none" stroke="currentColor" stroke-width="2.6"
            stroke-linecap="round" stroke-linejoin="round"
          />
        </svg>
        <span v-else-if="state === 'working'" :class="C.spin"></span>
      </button>

      <span :class="C.body" :style="layout.bodyStyle">
        <span :class="C.title">{{ title }}</span>
        <span :class="C.sub">{{ subtitle }}</span>
      </span>

      <span v-if="layout.showBrand" :class="C.brand" :style="layout.brandStyle" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="20" height="20">
          <path
            d="M12 2 4 5v6c0 5 3.4 9.3 8 10.7 4.6-1.4 8-5.7 8-10.7V5l-8-3Z"
            fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"
          />
          <path
            d="m8.8 12 2.2 2.2 4.4-4.4"
            fill="none" stroke="currentColor" stroke-width="1.6"
            stroke-linecap="round" stroke-linejoin="round"
          />
        </svg>
        <em>SmartCaptcha</em>
      </span>
    </div>

    <div v-if="state === 'working'" :class="C.bar" aria-hidden="true">
      <span :class="C.barFill" :style="{ width: `${Math.max(6, progress * 100)}%` }"></span>
    </div>
  </div>
</template>
