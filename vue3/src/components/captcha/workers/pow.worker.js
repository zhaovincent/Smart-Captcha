/**
 * 工作量证明计算线程。
 *
 * 放在 Worker 里跑的原因：难度 19 大约 50 万次哈希，主线程跑会让点击动画卡住；
 * Worker 里跑，复选框的转圈可以一直流畅，用户也能随时看到进度。
 */
import { createHasher } from '../utils/sha256.js'

self.onmessage = (event) => {
  const data = event.data || {}
  const prefix = String(data.prefix || '')
  const difficulty = Number(data.difficulty) || 0
  const start = Number(data.start) >>> 0
  const budget = Number(data.budget) || 1 << 23

  try {
    const hasher = createHasher(prefix)
    let lastReport = 0
    const result = hasher.solve(difficulty, start, budget, (tried) => {
      const now = Date.now()
      if (now - lastReport < 120) return
      lastReport = now
      self.postMessage({ type: 'progress', tried })
    })

    if (result) {
      self.postMessage({ type: 'done', nonce: result.nonce, hash: result.hash })
    } else {
      self.postMessage({ type: 'exhausted' })
    }
  } catch (e) {
    self.postMessage({ type: 'error', message: String((e && e.message) || e) })
  }
}
