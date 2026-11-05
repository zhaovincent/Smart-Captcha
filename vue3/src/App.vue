<script setup>
import { computed, nextTick, reactive, ref, watchEffect } from 'vue'
import BehaviorCaptcha from './components/BehaviorCaptcha.vue'
import { demoLogin, generateCaptcha, imageToDataUrl, verifyCaptcha } from './api/captcha.js'

const mode = ref('auto')
const modeOptions = [
  { value: 'auto', label: '随机一种' },
  { value: 'slider', label: '滑块拼图' },
  { value: 'click', label: '文字点选' },
  { value: 'rotate', label: '角度旋转' },
  { value: 'human', label: '人机验证' },
]

/** 面板展示方式：inline 内联（默认） / popup 弹窗 */
const displayMode = ref('inline')
const displayOptions = [
  { value: 'inline', label: '内联显示' },
  { value: 'popup', label: '弹窗显示' },
]

const useExternalBg = ref(false)
const externalBackgrounds = [
  '/backgrounds/bg-aurora.svg',
  '/backgrounds/bg-dunes.svg',
  '/backgrounds/bg-night.svg',
  '/backgrounds/bg-ocean.svg',
  '/backgrounds/bg-peak.svg',
  '/backgrounds/bg-forest.svg',
  '/backgrounds/bg-city.svg',
  '/backgrounds/bg-coral.svg',
  '/backgrounds/bg-cloud.svg',
  '/backgrounds/bg-geo.svg',
]

/** 点选字符集：后端默认 zh，这里按次覆盖演示 clickCharset 参数 */
const clickCharset = ref('zh')

/** 主题：light / dark / auto（跟随系统），与组件 theme prop 同语义；?theme= 优先，localStorage 记忆 */
const theme = ref('auto')
const themeOptions = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
  { value: 'auto', label: '跟随系统' },
]
{
  const q = new URLSearchParams(window.location.search)
  const t = q.get('theme')
  const saved = localStorage.getItem('sc-demo-theme')
  theme.value = ['light', 'dark', 'auto'].includes(t) ? t : ['light', 'dark', 'auto'].includes(saved) ? saved : 'auto'
}
function setTheme(value) {
  if (theme.value === value) return
  theme.value = value
  localStorage.setItem('sc-demo-theme', value)
  log(`主题切换为：${value === 'auto' ? '跟随系统' : value === 'dark' ? '深色' : '浅色'}`)
}
watchEffect(() => {
  document.documentElement.dataset.theme = theme.value
})

const captchaRef = ref(null)
const verified = ref(false)
const lastTicket = ref('')
const submitState = ref('')

const form = reactive({ username: 'demo', password: '123456' })

/** 组件通过 defineExpose 暴露的 verified / state 是响应式的，可直接绑到模板上 */
const captchaState = computed(() => captchaRef.value?.state ?? { verified: false, phase: 'idle', type: '', ticket: '', open: false })

/** 演示命令式查询：外部随时问组件"过了没" */
function queryState() {
  const ok = captchaRef.value?.isVerified?.() ?? false
  const snapshot = captchaRef.value?.getState?.()
  log(
    `isVerified() = ${ok}；phase=${snapshot?.phase}，type=${snapshot?.type || '-'}，ticket=${
      snapshot?.ticket ? `${snapshot.ticket.slice(0, 8)}…` : '无'
    }`,
    ok ? 'ok' : 'info',
  )
}

// 支持 ?demo=slider|click|rotate|human&bg=ext&display=popup 直接展开面板，方便联调与自动化测试
// 注意要在 setup 顶层同步读取，保证子组件首次渲染就能拿到 default-open
const autoOpen = ref(false)
{
  const q = new URLSearchParams(window.location.search)
  const demo = q.get('demo')
  if (demo) {
    mode.value = ['slider', 'click', 'rotate', 'human'].includes(demo) ? demo : 'auto'
    useExternalBg.value = q.get('bg') === 'ext'
    displayMode.value = q.get('display') === 'popup' ? 'popup' : 'inline'
    autoOpen.value = true
  }
}

const logs = reactive([])
function log(text, kind = 'info') {
  logs.unshift({ time: new Date().toLocaleTimeString('zh-CN', { hour12: false }), text, kind })
  if (logs.length > 30) logs.pop()
}

/** 外部背景图数组：随机取一张转 dataURL 交给后端抠图（由页面负责，组件不关心来源） */
async function pickBackground() {
  if (!useExternalBg.value || externalBackgrounds.length === 0) return null
  const url = externalBackgrounds[Math.floor(Math.random() * externalBackgrounds.length)]
  return imageToDataUrl(url)
}

/** 组件 @request：向端上要一份验证码数据 */
async function handleRequest({ type }, done) {
  try {
    const data = await generateCaptcha('', {
      type,
      backgroundImage: await pickBackground(),
      clickCharset: clickCharset.value,
    })
    log(`请求验证码（${type}）→ ${data.type}${data.click ? ` · ${data.click.charset}` : ''}`, 'info')
    done(data)
  } catch (e) {
    log(`获取验证码失败：${e.message}`, 'err')
    done(null, e)
  }
}

/** 组件 @verify：提交用户行为给端上校验 */
function handleVerify(payload, done) {
  verifyCaptcha('', payload).then(done).catch((e) => {
    log(`校验请求异常：${e.message}`, 'err')
    done(null, e)
  })
}

function onSuccess({ type, ticket }) {
  verified.value = true
  lastTicket.value = ticket
  submitState.value = ''
  log(`验证通过（${type}），获得 ticket: ${ticket.slice(0, 12)}…`, 'ok')
}

function onFail({ message }) {
  verified.value = false
  log(`验证失败：${message}`, 'err')
}

function onRefresh(type) {
  log(`获取新验证码，类型：${type}`)
}

/** 切换验证方式：只改配置，换图由组件监听 type 自行完成（否则会重复请求一次） */
function setMode(value) {
  if (mode.value === value) return
  mode.value = value
  clearVerified()
  const label = modeOptions.find((o) => o.value === value)?.label ?? value
  log(`切换验证方式：${label}`)
}

/** 切换面板展示方式：只改 prop，已展开的面板会原地换成另一种呈现方式 */
function setDisplay(value) {
  if (displayMode.value === value) return
  displayMode.value = value
  log(`面板展示方式切换为：${value === 'popup' ? '弹窗（遮罩居中）' : '内联（触发条下方）'}`)
}

/** 切换点选字符集：清掉已通过状态并重新取图（已展开时） */
function setClickCharset(value) {
  if (clickCharset.value === value) return
  clickCharset.value = value
  clearVerified()
  log(`点选字符集切换为 ${value === 'en' ? '英文' : '中文'}`)
  captchaRef.value?.refresh?.()
}

/** 切换背景图来源需要重新取图，等子组件拿到新配置后再刷新 */
async function toggleExternalBg() {
  useExternalBg.value = !useExternalBg.value
  clearVerified()
  await nextTick()
  captchaRef.value?.refresh?.()
}

function clearVerified() {
  verified.value = false
  lastTicket.value = ''
  submitState.value = ''
}

function resetDemo() {
  captchaRef.value?.reset()
  clearVerified()
  log('已重置演示')
}

async function onSubmit() {
  if (!verified.value || !lastTicket.value) {
    submitState.value = 'err'
    log('提交被拦截：尚未通过验证码', 'err')
    return
  }
  submitState.value = 'checking'
  try {
    // ticket 随业务请求一起提交，由服务端拦截器消费：前端不做任何"是否通过"的判断
    const data = await demoLogin('', lastTicket.value, { username: form.username, password: form.password })
    submitState.value = 'ok'
    log(`提交成功：ticket 已被业务后端消费，返回 token ${data.token.slice(0, 12)}…`, 'ok')
    resetCaptcha()
  } catch (e) {
    submitState.value = 'err'
    if (e.code === 4004) log(`业务接口拒绝（${e.message}），已重置验证码`, 'err')
    else log(`登录提交失败：${e.message}`, 'err')
    resetCaptcha()
  }
}

/** 清空本地验证状态并收起面板（ticket 在服务端是一次性的，本地状态必须同步失效） */
function resetCaptcha() {
  captchaRef.value?.reset()
  verified.value = false
  lastTicket.value = ''
}
</script>

<template>
  <div class="page">
    <header class="hero">
      <div class="hero-head">
        <h1>SmartCaptcha <span>·</span> 行为验证码</h1>
        <div class="segment theme-seg" role="group" aria-label="主题切换">
          <button
            v-for="opt in themeOptions"
            :key="opt.value"
            :class="{ active: theme === opt.value }"
            @click="setTheme(opt.value)"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>
      <p>
        Vue 3 组件 + Spring Boot 3 服务端。支持滑块拼图 / 文字点选 / 角度旋转 / 一键人机验证四种方式，
        可固定一种、可配置随机；背景图支持服务端程序化自动生成，也可外部传入图片数组。
        同时适配 PC 与手机 H5（指针事件 + 响应式布局）。
      </p>
    </header>

    <main class="layout">
      <!-- 左：模拟登录 -->
      <section class="card">
        <h2>模拟登录</h2>
        <div class="field">
          <label for="username">用户名</label>
          <input id="username" v-model="form.username" autocomplete="username" />
        </div>
        <div class="field">
          <label for="password">密码</label>
          <input id="password" v-model="form.password" type="password" autocomplete="current-password" />
        </div>

        <div class="captcha-slot">
          <BehaviorCaptcha
            ref="captchaRef"
            :type="mode"
            :types="['slider', 'click', 'rotate', 'human']"
            :display="displayMode"
            :theme="theme"
            bar-text="点击进行安全验证"
            :default-open="autoOpen"
            @request="handleRequest"
            @verify="handleVerify"
            @success="onSuccess"
            @fail="onFail"
            @refresh="onRefresh"
          />
        </div>

        <button class="submit-btn" :disabled="submitState === 'checking'" @click="onSubmit">
          {{ submitState === 'checking' ? '提交中…' : '登 录' }}
        </button>
        <p v-if="submitState === 'ok'" class="submit-tip ok">✓ 业务接口已放行，ticket 已在服务端一次性消费（重复使用会被 403 拒绝）</p>
        <p v-else-if="submitState === 'err'" class="submit-tip err">✗ 提交被拦截：ticket 缺失 / 过期 / 已使用，请重新完成验证</p>
        <p v-else class="submit-tip">没有有效 ticket 时，业务接口会被拦截（403 / code 4004）</p>
      </section>

      <!-- 右：配置与日志 -->
      <section class="card">
        <h2>组件配置</h2>
        <div class="field">
          <label>验证方式（type）</label>
          <div class="segment">
            <button
              v-for="opt in modeOptions"
              :key="opt.value"
              :class="{ active: mode === opt.value }"
              @click="setMode(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <div class="field">
          <label>面板展示方式（display）</label>
          <div class="segment">
            <button
              v-for="opt in displayOptions"
              :key="opt.value"
              :class="{ active: displayMode === opt.value }"
              @click="setDisplay(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>
        <p class="footnote" style="margin-top: -4px">
          默认 <code>inline</code>：面板在触发条下方就地展开；<code>popup</code>：居中浮层 + 遮罩，
          点遮罩 / 按 ESC 可关闭，验证通过后自动收起。组件与你的事件绑定方式完全不变。
        </p>

        <div class="field">
          <label>点选字符（click.charset）</label>
          <div class="segment">
            <button :class="{ active: clickCharset === 'zh' }" @click="setClickCharset('zh')">中文</button>
            <button :class="{ active: clickCharset === 'en' }" @click="setClickCharset('en')">English</button>
          </div>
        </div>
        <p class="footnote" style="margin-top: -4px">
          仅对“文字点选”生效。服务端默认 <code>zh</code>（<code>application.yaml</code> 的
          <code>smart-captcha.click.charset</code>），这里通过 generate 的 <code>clickCharset</code> 按次覆盖；
          英文模式下换成 A–Z 大写字母，提示文案同步为英文。
        </p>

        <div class="switch-row">
          <button class="switch" :class="{ on: useExternalBg }" @click="toggleExternalBg" aria-label="切换外部背景图"></button>
          <span>外部背景图数组（页面侧提供）</span>
        </div>
        <p class="footnote" style="margin-top: 6px">
          关闭：由后端 Java2D 程序化生成抽象背景；开启：页面从下面的图片数组随机取一张，转 base64 后随 @request 交给后端抠图（组件本身不碰网络）。
        </p>
        <div class="bg-thumbs">
          <img v-for="bg in externalBackgrounds" :key="bg" :src="bg" alt="示例背景" />
        </div>

        <div style="margin-top: 16px; display: flex; gap: 8px">
          <button class="ghost-btn" @click="captchaRef?.refresh?.({ open: true })">
            外部调用 refresh()
          </button>
          <button class="ghost-btn" @click="resetDemo">重置演示</button>
        </div>

        <div class="state-row">
          <span>验证状态</span>
          <span class="state-badge" :class="{ 'is-ok': captchaState.verified }">
            {{ captchaState.verified ? '已通过' : '未通过' }}
          </span>
          <code class="state-detail">phase: {{ captchaState.phase }}{{ captchaState.type ? ` · ${captchaState.type}` : '' }}</code>
          <button class="mini-btn" @click="queryState">queryState()</button>
        </div>

        <h2 style="margin-top: 22px">事件日志</h2>
        <ul class="log-list">
          <li v-for="(item, i) in logs" :key="i" :class="item.kind">
            <span class="t">{{ item.time }}</span>
            <span>{{ item.text }}</span>
          </li>
          <li v-if="logs.length === 0">
            <span>点击左侧“点击进行安全验证”开始体验</span>
          </li>
        </ul>
      </section>
    </main>

    <!-- 接口说明 -->
    <section class="card docs">
      <h2>后端接口（Spring Boot 3，默认 8080）</h2>
      <table class="api-table">
        <thead>
          <tr>
            <th>方法</th>
            <th>路径</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td class="method"><code class="m-get">GET</code></td>
            <td><code>/api/captcha/config</code></td>
            <td>获取配置：可用验证方式、画布尺寸、有效期、容差、当前存储实现等</td>
          </tr>
          <tr>
            <td class="method"><code class="m-post">POST</code></td>
            <td><code>/api/captcha/generate</code></td>
            <td>生成验证码。body：<code>{ "type": "slider|click|rotate|human|auto", "backgroundImage": "可选 dataURL", "clickCharset": "可选 zh|en" }</code></td>
          </tr>
          <tr>
            <td class="method"><code class="m-get">GET</code></td>
            <td><code>/api/captcha/generate?type=auto</code></td>
            <td>生成验证码（简化版，不含外部背景图）</td>
          </tr>
          <tr>
            <td class="method"><code class="m-post">POST</code></td>
            <td><code>/api/captcha/verify</code></td>
            <td>校验。滑块传 <code>{x, duration}</code>，点选传 <code>{points:[{x,y}]}</code>，旋转传 <code>{angle}</code>，人机验证传 <code>{pow:{nonce}, trace:{...}}</code>；通过返回一次性 ticket</td>
          </tr>
          <tr>
            <td class="method"><code class="m-post">POST</code></td>
            <td><code>/api/captcha/ticket/verify</code></td>
            <td>业务后端跨服务校验 ticket（一次性消费）；同进程可直接注入 <code>CaptchaService</code> 调用</td>
          </tr>
          <tr>
            <td class="method"><code class="m-post">POST</code></td>
            <td><code>/api/demo/login</code></td>
            <td>
              业务接口接入示例：标了 <code>@RequireCaptcha</code>，从请求头 <code>X-Captcha-Ticket</code> 取 ticket，
              缺失 / 过期 / 已用过一律 403（code 4004），进不到业务方法
            </td>
          </tr>
        </tbody>
      </table>
      <p class="footnote">
        左侧"登录"按钮走的就是 <code>/api/demo/login</code>：ticket 提交一次即被服务端消费，所以拿同一个 ticket 再点一次会得到 403。
        配置项见 <code>application.yaml</code> 的 <code>smart-captcha.*</code>：可开关验证方式、有效期、尝试次数、容差、拼图尺寸、点选字数与字符集、圆盘直径，
        以及 <code>background.dir</code> 指定服务端本地背景图目录、<code>store.type</code> 选择 memory / redis / redisson 存储。
      </p>
    </section>

    <!-- 组件事件 -->
    <section class="card docs">
      <h2>组件 Props</h2>
      <table class="api-table">
        <thead>
          <tr>
            <th>Prop</th>
            <th>类型 / 默认值</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>type</code></td>
            <td><code>String</code> · <code>'auto'</code></td>
            <td>固定一种验证方式，或 <code>auto</code> 从 <code>types</code> 随机</td>
          </tr>
          <tr>
            <td><code>types</code></td>
            <td><code>Array</code> · <code>['slider','click','rotate']</code></td>
            <td><code>type=auto</code> 时的随机池（要抽到人机验证需显式加 <code>'human'</code>）</td>
          </tr>
          <tr>
            <td><code>display</code></td>
            <td><code>String</code> · <code>'inline'</code></td>
            <td>
              <code>inline</code> 内联在触发条下方展开（默认）；<code>popup</code> 弹窗显示：
              居中浮层 + 遮罩，点遮罩 / ESC 关闭，通过后自动收起
            </td>
          </tr>
          <tr>
            <td><code>defaultOpen</code></td>
            <td><code>Boolean</code> · <code>false</code></td>
            <td>挂载后直接展开面板并加载</td>
          </tr>
          <tr>
            <td><code>autoRefresh</code></td>
            <td><code>Boolean</code> · <code>true</code></td>
            <td>验证失败后自动换一张</td>
          </tr>
          <tr>
            <td><code>barText</code> / <code>successText</code></td>
            <td><code>String</code></td>
            <td>触发条文案（默认「点击进行安全验证」/「验证成功」）</td>
          </tr>
          <tr>
            <td><code>eventTimeout</code></td>
            <td><code>Number</code> · <code>15000</code></td>
            <td>@request / @verify 回调超时（毫秒），避免父级忘记 done 时卡在加载态</td>
          </tr>
        </tbody>
      </table>

      <h2 style="margin-top: 22px">组件事件（组件内不发起任何请求）</h2>
      <table class="api-table">
        <thead>
          <tr>
            <th>事件</th>
            <th>签名</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>@request</code></td>
            <td><code>(params, done) =&gt; void</code></td>
            <td>要一份验证码，params 为 <code>{ type }</code>；拿到数据后调用 <code>done(challenge)</code>，失败调用 <code>done(null, error)</code></td>
          </tr>
          <tr>
            <td><code>@verify</code></td>
            <td><code>(payload, done) =&gt; void</code></td>
            <td>校验用户行为，payload 含 id/token/type/duration + x / points / angle；<code>done({ success, ticket, message })</code></td>
          </tr>
          <tr>
            <td><code>@success</code></td>
            <td><code>({ id, type, ticket }) =&gt; void</code></td>
            <td>验证通过，把 ticket 交给业务后端二次校验</td>
          </tr>
          <tr>
            <td><code>@fail</code></td>
            <td><code>({ message, attemptsLeft }) =&gt; void</code></td>
            <td>验证失败（组件会自动换一张，取决于 auto-refresh）</td>
          </tr>
          <tr>
            <td><code>@refresh</code></td>
            <td><code>(type) =&gt; void</code></td>
            <td>已获取到新验证码</td>
          </tr>
          <tr>
            <td><code>@close</code></td>
            <td><code>() =&gt; void</code></td>
            <td>面板被收起</td>
          </tr>
        </tbody>
      </table>
      <p class="footnote">
        本页的 <code>handleRequest / handleVerify</code> 在 <code>src/App.vue</code> 里，用 <code>src/api/captcha.js</code> 调用后端；
        换成你自己的后端、加签、走网关，只改这两个函数即可，组件无需改动。
      </p>

      <h2 style="margin-top: 22px">对外方法（通过模板 ref 调用）</h2>
      <table class="api-table">
        <thead>
          <tr>
            <th>方法</th>
            <th>签名</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>isVerified()</code></td>
            <td><code>() =&gt; boolean</code></td>
            <td>是否已通过验证（拿到 ticket 且之后没有刷新 / 重置）</td>
          </tr>
          <tr>
            <td><code>getState()</code></td>
            <td><code>() =&gt; { verified, phase, type, ticket, open, loading, display }</code></td>
            <td>当前状态快照，适合提交前兜底判断或埋点</td>
          </tr>
          <tr>
            <td><code>refresh(options?)</code></td>
            <td><code>({ open?: boolean }) =&gt; Promise&lt;state&gt;</code></td>
            <td>外部刷新验证码，旧 ticket 立即作废；<code>open: true</code> 时顺带展开面板</td>
          </tr>
          <tr>
            <td><code>reset()</code></td>
            <td><code>() =&gt; void</code></td>
            <td>清空状态并收起面板</td>
          </tr>
        </tbody>
      </table>
      <p class="footnote">
        右侧"验证状态"直接绑定了组件暴露的 <code>state</code>（响应式）；<code>queryState()</code> 走的是命令式的
        <code>isVerified()</code> / <code>getState()</code>，两种用法都可用。
      </p>
    </section>
  </div>
</template>
