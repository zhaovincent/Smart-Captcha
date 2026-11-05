<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import SliderStage from './captcha/SliderStage.vue'
import ClickStage from './captcha/ClickStage.vue'
import RotateStage from './captcha/RotateStage.vue'
import HumanStage from './captcha/HumanStage.vue'

/**
 * 行为验证码组件（滑块拼图 / 文字点选 / 角度旋转 / 一键人机验证）。
 *
 * 组件内不发起任何网络请求，全部通过外部事件完成：
 *  - @request(params, done)  需要一份验证码数据；done(challenge) 或 done(null, error)
 *  - @verify(payload, done)  校验用户行为；done(verifyResult) 或 done(null, error)
 * 由父级决定用哪个后端、如何取背景图、如何校验。
 *
 * 通过 ref 可调用：
 *  - isVerified() / getState()  查询是否已通过、当前阶段与 ticket
 *  - refresh({ open })          外部刷新验证码（open=true 时同时展开面板）
 *  - reset()                    清空状态并收起面板
 */
const props = defineProps({
  /** auto | slider | click | rotate | human */
  type: { type: String, default: 'auto' },
  /** type=auto 时的随机池 */
  types: { type: Array, default: () => ['slider', 'click', 'rotate'] },
  barText: { type: String, default: '点击进行安全验证' },
  successText: { type: String, default: '验证成功' },
  /** 验证失败后是否自动换一张 */
  autoRefresh: { type: Boolean, default: true },
  /**
   * 面板展示方式：
   *  - inline（默认）触发条下方就地展开
   *  - popup 弹窗：居中浮层 + 遮罩，可点遮罩 / 按 ESC 关闭
   */
  display: { type: String, default: 'inline' },
  /** 挂载后直接展开面板并加载 */
  defaultOpen: { type: Boolean, default: false },
  /**
   * 主题：
   *  - auto（默认）跟随系统 prefers-color-scheme
   *  - light 固定浅色 / dark 固定深色
   * 深浅配色由 --bc-* CSS 变量驱动，业务方可在外层覆盖变量做品牌定制。
   */
  theme: {
    type: String,
    default: 'auto',
    validator: (v) => ['auto', 'light', 'dark'].includes(v),
  },
  /** 外部事件回调超时（毫秒），避免父级忘记调用 done 时一直卡在加载态 */
  eventTimeout: { type: Number, default: 15000 },
})

const emit = defineEmits(['request', 'verify', 'success', 'fail', 'refresh', 'close'])

const TYPE_LABELS = { slider: '滑块拼图', click: '文字点选', rotate: '角度旋转', human: '人机验证' }

const instance = getCurrentInstance()

const open = ref(false)
const loading = ref(false)
const phase = ref('idle') // idle | loading | ready | checking | pass | fail
const challenge = ref(null)
const activeType = ref('')
const errorMsg = ref('')
const ticket = ref('')
const round = ref(0)

let reloadTimer = null
let closeTimer = null
let durationStart = 0
/** 请求序号：type 切换/重置后，旧的响应必须丢弃，否则面板会显示上一张（旧类型的）验证码 */
let requestSeq = 0

const panelRef = ref(null)
/** popup 模式（也接受 modal / dialog / overlay 这几个常见别名） */
const isPopup = computed(() => ['popup', 'modal', 'dialog', 'overlay'].includes(props.display))

const stageComponent = computed(() => {
  if (activeType.value === 'slider') return SliderStage
  if (activeType.value === 'click') return ClickStage
  if (activeType.value === 'human') return HumanStage
  return RotateStage
})

const typeLabel = computed(() => TYPE_LABELS[activeType.value] ?? '安全验证')

/** 主题作用域类：触发条、内联面板、弹窗（Teleport 到 body 后脱离 .bc，必须单独携带） */
const themeClass = computed(() => `sc-${props.theme}`)

const captchaRatio = computed(() => {
  const c = challenge.value
  return c && c.width && c.height ? `${c.width} / ${c.height}` : '1.8'
})

const tip = computed(() => challenge.value?.tip ?? '')

/** 读取父级是否绑定了某个事件（defineEmits 声明的不会出现在 attrs 里，故查 vnode.props） */
function listenerOf(name) {
  const vnodeProps = instance?.vnode?.props
  if (!vnodeProps) return null
  const key = `on${name.charAt(0).toUpperCase()}${name.slice(1)}`
  const fn = vnodeProps[key]
  if (!fn) return null
  return Array.isArray(fn) ? fn[0] : fn
}

/**
 * 触发外部事件并等待父级回调：done(data) 成功，done(null, error) 失败。
 */
function invoke(name, payload) {
  const listener = listenerOf(name)
  if (!listener) {
    const label = name === 'request' ? '获取验证码' : '校验验证码'
    return Promise.reject(new Error(`组件未绑定 @${name} 事件，无法${label}`))
  }
  return new Promise((resolve, reject) => {
    let settled = false
    const timeout = props.eventTimeout
    const timer = timeout > 0
      ? setTimeout(() => {
          if (settled) return
          settled = true
          reject(new Error(`@${name} 事件响应超时（${timeout}ms）`))
        }, timeout)
      : null

    const finish = (data, error) => {
      if (settled) return
      settled = true
      if (timer) clearTimeout(timer)
      if (error) reject(error)
      else if (!data) reject(new Error(`@${name} 事件未返回数据`))
      else resolve(data)
    }

    try {
      listener(payload, finish)
    } catch (e) {
      finish(null, e)
    }
  })
}

function toggle() {
  if (open.value) {
    close()
  } else {
    open.value = true
    if (!challenge.value || phase.value === 'fail' || phase.value === 'pass') {
      load()
    }
  }
}

function close() {
  clearTimeout(closeTimer)
  open.value = false
  emit('close')
}

/** 点遮罩关闭：校验中不允许关闭，避免请求回来时面板已经没了 */
function onBackdrop() {
  if (phase.value === 'checking') return
  close()
}

function onKeydown(e) {
  if (e.key !== 'Escape' || !open.value || !isPopup.value) return
  onBackdrop()
}

// ---------------------------------------------------------------- popup：滚动锁 / 焦点 / ESC

let bodyOverflow = null

function lockPageScroll(lock) {
  if (typeof document === 'undefined') return
  if (lock) {
    if (bodyOverflow !== null) return
    bodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else if (bodyOverflow !== null) {
    document.body.style.overflow = bodyOverflow
    bodyOverflow = null
  }
}

function bindPopup(active) {
  if (typeof document === 'undefined') return
  lockPageScroll(active)
  if (active) document.addEventListener('keydown', onKeydown)
  else document.removeEventListener('keydown', onKeydown)
}

watch(
  () => open.value && isPopup.value,
  (active) => {
    bindPopup(active)
    if (active) nextTick(() => panelRef.value?.focus?.())
  },
)

// popup 模式验证通过后稍作停留自动收起（inline 模式下保留成功态，方便用户看到结果）
watch(
  () => phase.value,
  (value) => {
    if (value !== 'pass' || !isPopup.value || !open.value) return
    clearTimeout(closeTimer)
    closeTimer = setTimeout(close, 900)
  },
)

async function load() {
  clearTimeout(reloadTimer)
  const seq = ++requestSeq
  loading.value = true
  phase.value = 'loading'
  errorMsg.value = ''
  ticket.value = ''
  try {
    const data = await invoke('request', { type: resolveType() })
    if (seq !== requestSeq) return // 期间又发起了新请求（例如切换了验证方式），丢弃本次结果
    challenge.value = data
    activeType.value = data.type
    phase.value = 'ready'
    round.value += 1
    durationStart = performance.now()
    emit('refresh', data.type)
  } catch (e) {
    if (seq !== requestSeq) return
    challenge.value = null
    phase.value = 'fail'
    errorMsg.value = e?.message || '验证码加载失败'
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function resolveType() {
  if (props.type && props.type !== 'auto') return props.type
  const pool = props.types.length ? props.types : ['slider', 'click', 'rotate']
  return pool[Math.floor(Math.random() * pool.length)]
}

async function submit(payload) {
  if (!challenge.value || phase.value === 'checking') return
  phase.value = 'checking'
  const duration = Math.round(performance.now() - durationStart)
  try {
    const data = await invoke('verify', {
      id: challenge.value.id,
      token: challenge.value.token,
      type: challenge.value.type,
      duration,
      ...payload,
    })
    if (data.success) {
      phase.value = 'pass'
      ticket.value = data.ticket
      emit('success', { id: challenge.value.id, type: challenge.value.type, ticket: data.ticket })
    } else {
      phase.value = 'fail'
      errorMsg.value = data.message || '验证失败'
      emit('fail', { message: errorMsg.value, attemptsLeft: data.attemptsLeft })
      scheduleReload()
    }
  } catch (e) {
    phase.value = 'fail'
    errorMsg.value = e?.message || '验证失败，请重试'
    emit('fail', { message: errorMsg.value })
    if (e?.code === 4002) scheduleReload()
  }
}

function scheduleReload() {
  if (!props.autoRefresh) return
  clearTimeout(reloadTimer)
  reloadTimer = setTimeout(load, 1100)
}

function onRelease(payload) {
  if (!payload) return
  if (activeType.value === 'slider') {
    const c = challenge.value
    const x = payload.progress * (c.width - c.slider.pieceWidth)
    submit({ x: Math.round(x * 10) / 10, duration: payload.duration })
  } else if (activeType.value === 'rotate') {
    submit({ angle: payload.angle })
  }
}

function onClickComplete(payload) {
  // 点选回调的是坐标数组；人机验证回调的是 { nonce, trace }
  if (activeType.value === 'human') {
    submit({ nonce: payload?.nonce ?? null, trace: payload?.trace ?? null })
    return
  }
  submit({ points: payload })
}

function reset() {
  clearTimeout(reloadTimer)
  clearTimeout(closeTimer)
  requestSeq += 1 // 作废进行中的请求，避免重置后又把旧题目填回来
  loading.value = false
  phase.value = 'idle'
  challenge.value = null
  activeType.value = ''
  errorMsg.value = ''
  ticket.value = ''
  open.value = false
}

// ---------------------------------------------------------------- 对外查询 / 控制

/** 是否已通过验证：拿到 ticket 且之后没有刷新/重置过。 */
const verified = computed(() => phase.value === 'pass' && ticket.value !== '')

/** 状态快照，外部可用来做按钮禁用、埋点、UI 文案。 */
const state = computed(() => ({
  verified: verified.value,
  phase: phase.value,
  type: activeType.value || challenge.value?.type || '',
  ticket: ticket.value,
  open: open.value,
  loading: loading.value,
  display: isPopup.value ? 'popup' : 'inline',
}))

/** 查询是否已通过验证（命令式，适合在提交前做一次兜底判断）。 */
function isVerified() {
  return verified.value
}

/** 查询当前完整状态快照。 */
function getState() {
  return state.value
}

/**
 * 外部刷新：重新取一张验证码，之前拿到的 ticket 立即作废。
 *
 * @param {{ open?: boolean }} [options] open=true 时顺带展开面板（"换一张"按钮常用）
 * @returns {Promise<object>} 刷新后的状态快照
 */
async function refresh(options = {}) {
  if (options?.open) {
    open.value = true
  }
  await load()
  return state.value
}

onBeforeUnmount(() => {
  clearTimeout(reloadTimer)
  clearTimeout(closeTimer)
  bindPopup(false)
})

/**
 * 外部把 type 改掉时，面板必须立刻按新配置换一张。
 * 父级在改完 prop 后同步调用 refresh() 是拿不到新值的（prop 要等父组件重渲染才更新），
 * 所以"配置变化 → 换图"这件事由组件自己负责。
 */
watch(
  () => props.type,
  () => {
    if (open.value) {
      load()
      return
    }
    // 面板未展开：作废进行中的请求并清掉旧题目，下次展开时按新配置加载
    clearTimeout(reloadTimer)
    requestSeq += 1
    loading.value = false
    phase.value = 'idle'
    challenge.value = null
    activeType.value = ''
    errorMsg.value = ''
    ticket.value = ''
  },
)

onMounted(() => {
  if (props.defaultOpen) {
    open.value = true
    load()
  }
})

defineExpose({
  // 查询
  isVerified,
  getState,
  verified,
  state,
  ticket,
  phase,
  // 控制
  refresh,
  reset,
})
</script>

<template>
  <div class="bc" :class="[themeClass, { 'is-pass': phase === 'pass' }]">
    <button type="button" class="bc-bar" :aria-expanded="open ? 'true' : 'false'" @click="toggle">
      <span class="bc-bar-icon" aria-hidden="true">
        <svg v-if="phase !== 'pass'" viewBox="0 0 24 24" width="18" height="18">
          <path
            d="M12 2 4 5v6c0 5 3.4 9.3 8 10.7 4.6-1.4 8-5.7 8-10.7V5l-8-3Z"
            fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"
          />
          <path d="m8.8 12 2.2 2.2 4.4-4.4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <svg v-else viewBox="0 0 24 24" width="18" height="18">
          <path d="m5 12.5 4.5 4.5L19 7.5" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </span>
      <span class="bc-bar-text">{{ phase === 'pass' ? successText : barText }}</span>
      <span v-if="phase !== 'pass'" class="bc-bar-arrow" aria-hidden="true">›</span>
    </button>

    <Teleport to="body" :disabled="!isPopup">
      <transition :name="isPopup ? 'bc-pop' : 'bc-drop'">
        <div v-if="open" :class="[themeClass, isPopup ? 'bc-overlay' : 'bc-inline']">
          <div v-if="isPopup" class="bc-mask" @click="onBackdrop"></div>
          <div
            ref="panelRef"
            class="bc-panel"
            :class="{ 'is-popup': isPopup }"
            role="dialog"
            :aria-modal="isPopup ? 'true' : undefined"
            aria-label="行为验证码"
            tabindex="-1"
          >
            <div class="bc-head">
              <span class="bc-head-title">安全验证</span>
              <span class="bc-chip">{{ typeLabel }}</span>
              <div class="bc-head-actions">
                <button type="button" class="bc-icon-btn" title="刷新验证码" :disabled="loading" @click="load">
                  <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                    <path d="M20 12a8 8 0 1 1-2.34-5.66M20 4v4h-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </button>
                <button type="button" class="bc-icon-btn" title="收起" @click="close">
                  <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                    <path d="M6 6l12 12M18 6 6 18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                  </svg>
                </button>
              </div>
            </div>

            <div class="bc-stage-area" :style="{ '--captcha-ratio': captchaRatio }">
              <div v-if="phase === 'loading'" class="bc-placeholder">
                <span class="bc-spinner" aria-hidden="true"></span>
                <span>验证码加载中…</span>
              </div>

              <div v-else-if="phase === 'pass'" class="bc-placeholder bc-pass-block">
                <svg viewBox="0 0 24 24" width="42" height="42" aria-hidden="true">
                  <circle cx="12" cy="12" r="10" fill="#dcfce7" />
                  <path d="m7.5 12.5 3 3 6-6.5" fill="none" stroke="#16a34a" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                <span>验证通过</span>
              </div>

              <component
                :is="stageComponent"
                v-else-if="challenge"
                :key="round"
                :challenge="challenge"
                :disabled="phase === 'checking'"
                @release="onRelease"
                @complete="onClickComplete"
              />

              <div v-else class="bc-placeholder bc-error-block">
                <span>⚠ {{ errorMsg || '暂无验证码' }}</span>
              </div>
            </div>

            <div class="bc-footer">
              <span v-if="phase === 'checking'" class="bc-msg">校验中…</span>
              <span v-else-if="phase === 'fail'" class="bc-msg is-error">{{ errorMsg }}</span>
              <span v-else-if="phase === 'ready'" class="bc-msg is-muted">{{ tip }}</span>
              <span v-else class="bc-msg is-muted"></span>
              <button type="button" class="bc-link" :disabled="loading" @click="load">换一张</button>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
/* ---------- 主题变量（浅色默认值） ----------
 * 作用域：触发条 .bc / 内联容器 .bc-inline / 弹窗容器 .bc-overlay（Teleport 后脱离 .bc）。
 * 业务方可在任意外层覆盖这些变量做品牌定制，例如：
 * .my-page { --bc-primary: #2563eb; }
 */
.bc,
.bc-inline,
.bc-overlay {
  --bc-bar-bg: #f8fafc;
  --bc-bar-border: #e2e8f0;
  --bc-bar-text: #475569;
  --bc-bar-hover-bg: #f5f6ff;
  --bc-bar-hover-border: #c7d2fe;
  --bc-ok-bg: #f0fdf4;
  --bc-ok-border: #bbf7d0;
  --bc-ok-text: #15803d;
  --bc-surface: #ffffff;
  --bc-surface-border: #e2e8f0;
  --bc-text: #0f172a;
  --bc-text-muted: #475569;
  --bc-text-faint: #94a3b8;
  --bc-hover: #f1f5f9;
  --bc-primary: #4f46e5;
  --bc-primary-mid: #818cf8;
  --bc-primary-weak: #c7d2fe;
  --bc-primary-soft: #eef2ff;
  --bc-primary-ring: rgba(79, 70, 229, 0.15);
  --bc-canvas: #eef2f7;
  --bc-track-bg: #eef2f7;
  --bc-track-border: #e2e8f0;
  --bc-word-bg: #f1f5f9;
  --bc-word-border: #e2e8f0;
  --bc-word-text: #0f172a;
  --bc-dot-alt: #312e81;
  --bc-success: #16a34a;
  --bc-success-soft: #dcfce7;
  --bc-danger: #dc2626;
  --bc-mask: rgba(15, 23, 42, 0.45);
  --bc-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
  --bc-shadow-popup: 0 24px 60px rgba(15, 23, 42, 0.28);
  --bc-handle-shadow: 0 2px 8px rgba(79, 70, 229, 0.45);
}

/* 深色主题：theme="dark" */
.sc-dark.bc,
.sc-dark.bc-inline,
.sc-dark.bc-overlay {
  --bc-bar-bg: #243044;
  --bc-bar-border: #33415c;
  --bc-bar-text: #cbd5e1;
  --bc-bar-hover-bg: #2b3a55;
  --bc-bar-hover-border: #4c5a7d;
  --bc-ok-bg: rgba(74, 222, 128, 0.12);
  --bc-ok-border: rgba(74, 222, 128, 0.35);
  --bc-ok-text: #4ade80;
  --bc-surface: #161e2e;
  --bc-surface-border: #2c3a55;
  --bc-text: #e2e8f0;
  --bc-text-muted: #a9b4c9;
  --bc-text-faint: #64748b;
  --bc-hover: #223050;
  --bc-primary: #818cf8;
  --bc-primary-mid: #818cf8;
  --bc-primary-weak: #4c5698;
  --bc-primary-soft: rgba(129, 140, 248, 0.16);
  --bc-primary-ring: rgba(129, 140, 248, 0.25);
  --bc-canvas: #0f172a;
  --bc-track-bg: #223050;
  --bc-track-border: #33415c;
  --bc-word-bg: #223050;
  --bc-word-border: #33415c;
  --bc-word-text: #e2e8f0;
  --bc-dot-alt: #1e1b4b;
  --bc-success: #4ade80;
  --bc-success-soft: #14532d;
  --bc-danger: #f87171;
  --bc-mask: rgba(2, 6, 23, 0.65);
  --bc-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
  --bc-shadow-popup: 0 24px 60px rgba(0, 0, 0, 0.6);
  --bc-handle-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
}

/* 深色主题：theme="auto" 跟随系统 */
@media (prefers-color-scheme: dark) {
  .sc-auto.bc,
  .sc-auto.bc-inline,
  .sc-auto.bc-overlay {
    --bc-bar-bg: #243044;
    --bc-bar-border: #33415c;
    --bc-bar-text: #cbd5e1;
    --bc-bar-hover-bg: #2b3a55;
    --bc-bar-hover-border: #4c5a7d;
    --bc-ok-bg: rgba(74, 222, 128, 0.12);
    --bc-ok-border: rgba(74, 222, 128, 0.35);
    --bc-ok-text: #4ade80;
    --bc-surface: #161e2e;
    --bc-surface-border: #2c3a55;
    --bc-text: #e2e8f0;
    --bc-text-muted: #a9b4c9;
    --bc-text-faint: #64748b;
    --bc-hover: #223050;
    --bc-primary: #818cf8;
    --bc-primary-mid: #818cf8;
    --bc-primary-weak: #4c5698;
    --bc-primary-soft: rgba(129, 140, 248, 0.16);
    --bc-primary-ring: rgba(129, 140, 248, 0.25);
    --bc-canvas: #0f172a;
    --bc-track-bg: #223050;
    --bc-track-border: #33415c;
    --bc-word-bg: #223050;
    --bc-word-border: #33415c;
    --bc-word-text: #e2e8f0;
    --bc-dot-alt: #1e1b4b;
    --bc-success: #4ade80;
    --bc-success-soft: #14532d;
    --bc-danger: #f87171;
    --bc-mask: rgba(2, 6, 23, 0.65);
    --bc-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
    --bc-shadow-popup: 0 24px 60px rgba(0, 0, 0, 0.6);
    --bc-handle-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
  }
}

.bc {
  width: 100%;
  max-width: 360px;
  font-family: inherit;
}

/* ---------- 触发条 ---------- */
.bc-bar {
  width: 100%;
  height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid var(--bc-bar-border);
  border-radius: 10px;
  background: var(--bc-bar-bg);
  color: var(--bc-bar-text);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.bc-bar:hover {
  border-color: var(--bc-bar-hover-border);
  background: var(--bc-bar-hover-bg);
}

.bc.is-pass .bc-bar {
  border-color: var(--bc-ok-border);
  background: var(--bc-ok-bg);
  color: var(--bc-ok-text);
}

.bc-bar-icon {
  display: flex;
  color: var(--bc-primary);
}

.bc.is-pass .bc-bar-icon {
  color: var(--bc-success);
}

.bc-bar-text {
  flex: 1;
  text-align: left;
}

.bc-bar-arrow {
  font-size: 18px;
  color: var(--bc-text-faint);
}

/* ---------- 面板 ---------- */
.bc-panel {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid var(--bc-surface-border);
  border-radius: 14px;
  background: var(--bc-surface);
  box-shadow: var(--bc-shadow);
}

/* ---------- 弹窗模式 ---------- */
.bc-overlay {
  position: fixed;
  inset: 0;
  z-index: 2147483000; /* 高于绝大多数业务浮层 */
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.bc-mask {
  position: absolute;
  inset: 0;
  background: var(--bc-mask);
  backdrop-filter: blur(2px);
}

.bc-panel.is-popup {
  position: relative;
  margin-top: 0;
  width: min(384px, calc(100vw - 40px));
  padding: 14px;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  box-shadow: var(--bc-shadow-popup);
  outline: none;
}

.bc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.bc-head-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--bc-text);
}

.bc-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--bc-primary-soft);
  color: var(--bc-primary);
}

.bc-head-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.bc-icon-btn {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--bc-text-muted);
  cursor: pointer;
  transition: all 0.15s;
}

.bc-icon-btn:hover {
  background: var(--bc-hover);
  color: var(--bc-text);
}

.bc-icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.bc-stage-area {
  width: 100%;
}

.bc-placeholder {
  min-height: 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-radius: 10px;
  background: var(--bc-track-bg);
  color: var(--bc-text-muted);
  font-size: 13px;
}

.bc-pass-block {
  color: var(--bc-success);
  font-size: 15px;
  font-weight: 600;
}

/* 通过图标：颜色交给主题变量（CSS 优先级高于 SVG 表现属性） */
.bc-pass-block circle {
  fill: var(--bc-success-soft);
}

.bc-pass-block svg path {
  stroke: var(--bc-success);
}

.bc-error-block {
  color: var(--bc-danger);
}

.bc-spinner {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 3px solid var(--bc-track-border);
  border-top-color: var(--bc-primary);
  animation: bc-spin 0.8s linear infinite;
}

@keyframes bc-spin {
  to {
    transform: rotate(360deg);
  }
}

.bc-footer {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12.5px;
  min-height: 20px;
}

.bc-msg {
  color: var(--bc-text-muted);
}

.bc-msg.is-error {
  color: var(--bc-danger);
}

.bc-msg.is-muted {
  color: var(--bc-text-faint);
}

.bc-link {
  border: none;
  background: none;
  color: var(--bc-primary);
  font-size: 12.5px;
  cursor: pointer;
  padding: 2px 4px;
}

.bc-link:disabled {
  color: var(--bc-primary-weak);
  cursor: not-allowed;
}

/* ---------- 过渡 ---------- */
.bc-drop-enter-active,
.bc-drop-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.bc-drop-enter-from,
.bc-drop-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* popup：遮罩淡入，面板轻微放大 */
.bc-pop-enter-active,
.bc-pop-leave-active {
  transition: opacity 0.2s ease;
}

.bc-pop-enter-active .bc-panel,
.bc-pop-leave-active .bc-panel {
  transition: transform 0.2s ease;
}

.bc-pop-enter-from,
.bc-pop-leave-to {
  opacity: 0;
}

.bc-pop-enter-from .bc-panel,
.bc-pop-leave-to .bc-panel {
  transform: scale(0.94) translateY(6px);
}

/* ---------- 移动端 ---------- */
@media (max-width: 420px) {
  .bc {
    max-width: 100%;
  }

  .bc-overlay {
    padding: 12px;
  }

  .bc-panel.is-popup {
    width: calc(100vw - 24px);
    max-height: calc(100vh - 24px);
  }
}
</style>
