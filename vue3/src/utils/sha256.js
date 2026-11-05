/**
 * 纯 JS 的 SHA-256（+ 前导零位判定），供工作量证明使用。
 *
 * 为什么不用 WebCrypto：crypto.subtle.digest 是异步的，一次调用要跨 Promise，
 * 而 PoW 需要在几十万次循环里做哈希。这里用一个预分配缓冲区的同步实现，
 * 单次约几百纳秒，浏览器里算 2^18 次大约零点几秒。
 *
 * 输入固定为 `SC1|token|salt|nonce`，都是 ASCII，所以按 UTF-8 编码与按字节
 * 处理完全等价 —— 前缀只编码一次，循环里只写 nonce 的十进制数字。
 */

// SHA-256 轮常量
const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
])

const H0 = new Uint32Array([
  0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
])

const NONCE_DIGITS = 10 // 32 位无符号整数的最大十进制长度

/**
 * 创建一个针对固定前缀的哈希器。前缀形如 `SC1|token|salt|`。
 * 返回的对象可复用，内部缓冲区不会重复分配。
 */
export function createHasher(prefix) {
  const prefixBytes = new TextEncoder().encode(prefix)
  const maxLen = prefixBytes.length + NONCE_DIGITS
  const buffer = new Uint8Array(Math.ceil((maxLen + 9) / 64) * 64 + 64)
  buffer.set(prefixBytes, 0)

  const w = new Uint32Array(64)
  const h = new Uint32Array(8)
  const out = new Uint8Array(32)

  /** 把 nonce 的十进制数字写进缓冲区，返回位数 */
  function writeNonce(nonce) {
    let n = nonce >>> 0
    let digits = 1
    let t = n
    while (t >= 10) {
      t = Math.floor(t / 10)
      digits++
    }
    let p = prefixBytes.length + digits - 1
    let m = n
    for (let i = 0; i < digits; i++) {
      buffer[p--] = 48 + (m % 10)
      m = Math.floor(m / 10)
    }
    return digits
  }

  /** 压缩一个 64 字节块 */
  function compress(offset) {
    for (let i = 0, j = offset; i < 16; i++, j += 4) {
      w[i] = (buffer[j] << 24) | (buffer[j + 1] << 16) | (buffer[j + 2] << 8) | buffer[j + 3]
    }
    for (let i = 16; i < 64; i++) {
      const x = w[i - 15]
      const y = w[i - 2]
      const s0 = ((x >>> 7) | (x << 25)) ^ ((x >>> 18) | (x << 14)) ^ (x >>> 3)
      const s1 = ((y >>> 17) | (y << 15)) ^ ((y >>> 19) | (y << 13)) ^ (y >>> 10)
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0
    }

    let a = h[0]
    let b = h[1]
    let c = h[2]
    let d = h[3]
    let e = h[4]
    let f = h[5]
    let g = h[6]
    let hh = h[7]

    for (let i = 0; i < 64; i++) {
      const s1 = ((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7))
      const ch = (e & f) ^ (~e & g)
      const t1 = (hh + s1 + ch + K[i] + w[i]) | 0
      const s0 = ((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10))
      const maj = (a & b) ^ (a & c) ^ (b & c)
      const t2 = (s0 + maj) | 0

      hh = g
      g = f
      f = e
      e = (d + t1) | 0
      d = c
      c = b
      b = a
      a = (t1 + t2) | 0
    }

    h[0] = (h[0] + a) | 0
    h[1] = (h[1] + b) | 0
    h[2] = (h[2] + c) | 0
    h[3] = (h[3] + d) | 0
    h[4] = (h[4] + e) | 0
    h[5] = (h[5] + f) | 0
    h[6] = (h[6] + g) | 0
    h[7] = (h[7] + hh) | 0
  }

  /** 计算 `prefix + nonce` 的 SHA-256，结果写入 out 并返回它 */
  function hash(nonce) {
    const len = prefixBytes.length + writeNonce(nonce)
    const total = Math.ceil((len + 9) / 64) * 64

    buffer[len] = 0x80
    for (let i = len + 1; i < total - 8; i++) buffer[i] = 0
    // 64 位大端长度：高 32 位恒为 0，低 32 位放比特数
    const bits = len * 8
    buffer[total - 8] = 0
    buffer[total - 7] = 0
    buffer[total - 6] = 0
    buffer[total - 5] = 0
    buffer[total - 4] = (bits >>> 24) & 0xff
    buffer[total - 3] = (bits >>> 16) & 0xff
    buffer[total - 2] = (bits >>> 8) & 0xff
    buffer[total - 1] = bits & 0xff

    h.set(H0)
    for (let offset = 0; offset < total; offset += 64) compress(offset)

    for (let i = 0; i < 8; i++) {
      out[i * 4] = (h[i] >>> 24) & 0xff
      out[i * 4 + 1] = (h[i] >>> 16) & 0xff
      out[i * 4 + 2] = (h[i] >>> 8) & 0xff
      out[i * 4 + 3] = h[i] & 0xff
    }
    return out
  }

  /** 最近一次 hash 结果是否满足前 bits 位为 0 */
  function meets(bits) {
    if (bits <= 0) return true
    const full = bits >>> 3
    for (let i = 0; i < full; i++) {
      if (out[i] !== 0) return false
    }
    const rest = bits & 7
    if (rest === 0) return true
    return (out[full] & ((0xff << (8 - rest)) & 0xff)) === 0
  }

  function hex() {
    let s = ''
    for (let i = 0; i < 32; i++) s += out[i].toString(16).padStart(2, '0')
    return s
  }

  /**
   * 从 start 开始找解。
   *
   * @param {number} difficulty 前导 0 位数
   * @param {number} start      起始 nonce
   * @param {number} budget     最多尝试次数
   * @param {(tried:number)=>void} [onTick] 每批回调一次，用于上报进度
   * @returns {{nonce:number, hash:string}|null}
   */
  function solve(difficulty, start = 0, budget = 1 << 22, onTick = null) {
    const batch = 2048
    let nonce = start >>> 0
    let tried = 0
    while (tried < budget) {
      const end = Math.min(tried + batch, budget)
      for (; tried < end; tried++) {
        hash(nonce)
        if (meets(difficulty)) {
          return { nonce: nonce >>> 0, hash: hex() }
        }
        nonce = (nonce + 1) >>> 0
      }
      if (onTick) onTick(tried)
    }
    return null
  }

  return { hash, meets, hex, solve }
}
