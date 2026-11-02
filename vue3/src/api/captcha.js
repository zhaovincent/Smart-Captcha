/**
 * SmartCaptcha 前端 API 封装。
 * apiBase 为后端地址：开发环境留空走 vite 代理；生产可传 https://api.xxx.com
 */

export class CaptchaApiError extends Error {
  constructor(code, message) {
    super(message)
    this.name = 'CaptchaApiError'
    this.code = code
  }
}

async function request(apiBase, path, options = {}) {
  let res
  try {
    res = await fetch((apiBase || '').replace(/\/$/, '') + path, options)
  } catch (e) {
    throw new CaptchaApiError(-1, '网络异常，无法连接验证服务')
  }
  let json
  try {
    json = await res.json()
  } catch (e) {
    throw new CaptchaApiError(-1, `验证服务响应异常（HTTP ${res.status}）`)
  }
  if (json.code !== 0) {
    throw new CaptchaApiError(json.code, json.message || '请求失败')
  }
  return json.data
}

/** 读取服务端配置（可用验证方式、尺寸、有效期等）。 */
export function fetchCaptchaConfig(apiBase) {
  return request(apiBase, '/api/captcha/config')
}

/**
 * 生成验证码。
 * @param {string} apiBase 后端地址
 * @param {{type?: string, backgroundImage?: string, clickCharset?: string}} payload
 *        type=slider|click|rotate|auto；clickCharset=zh|en（覆盖服务端默认值）
 */
export function generateCaptcha(apiBase, payload = {}) {
  return request(apiBase, '/api/captcha/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

/**
 * 校验验证码。
 * slider: { x, trail? }；click: { points }；rotate: { angle }
 */
export function verifyCaptcha(apiBase, { id, token, type, duration, x, y, angle, points, trail }) {
  return request(apiBase, '/api/captcha/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id, token, type, duration, x, y, angle, points, trail }),
  })
}

/**
 * 业务侧消费 ticket 的 HTTP 接口。
 * 注意：这是给**业务后端**调用的（服务间调用 / 另一个服务），不要在前端直接调——
 * 前端调等于"自己给自己发通行证"，绕过验证没有任何成本。
 */
export function verifyTicket(apiBase, ticket) {
  return request(apiBase, '/api/captcha/ticket/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ticket }),
  })
}

/**
 * 示例业务接口：登录。ticket 放在请求头里，由业务后端的拦截器一次性消费。
 * 返回 403 + code 4004 表示 ticket 缺失/过期/已用过，需要重新过验证码。
 */
export function demoLogin(apiBase, ticket, credentials = {}) {
  return request(apiBase, '/api/demo/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Captcha-Ticket': ticket },
    body: JSON.stringify(credentials),
  })
}

/**
 * 把外部背景图 URL 转为 dataURL，随生成请求发给后端抠图。
 * 跨域图片要求服务端允许 CORS；失败时返回 null（回退为服务端自动生成背景）。
 */
export function imageToDataUrl(url, maxWidth = 720) {
  return new Promise((resolve) => {
    if (!url) {
      resolve(null)
      return
    }
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => {
      try {
        const scale = Math.min(1, maxWidth / img.naturalWidth)
        const canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.round(img.naturalWidth * scale))
        canvas.height = Math.max(1, Math.round(img.naturalHeight * scale))
        canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height)
        resolve(canvas.toDataURL('image/jpeg', 0.92))
      } catch (e) {
        resolve(null)
      }
    }
    img.onerror = () => resolve(null)
    img.src = url
  })
}
