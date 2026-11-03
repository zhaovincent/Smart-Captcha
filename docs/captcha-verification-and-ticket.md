# 行为验证码校验逻辑与 Ticket 机制安全性分析

> 适用版本：1.9.0（smart-captcha-core / starter / server）
> 本文回答三个问题：**校验是怎么做的？ticket 是怎么产生的？能不能被伪造？**

---

## 1. 整体流程

```
┌────────┐  ①GET /api/captcha/generate   ┌────────────┐
│  前端   │ ─────────────────────────────▶│  服务端     │
│  组件   │ ◀───── challenge(id/token/图) │ CaptchaEngine│
│        │                               │      │ save │
│  ②用户完成行为  ▼                      │      ▼      │
│        │  ③POST /api/captcha/verify    │ CaptchaStore │
│        │ ─────(id + token + 行为数据)──▶│ (会话+答案)  │
│        │ ◀──── VerifyResult(ticket)────│              │
│        │                               └────────────┘
│  ④提交业务请求 + ticket ──▶ 业务接口 consumeTicket(ticket)
│        │                               一次性消费，通过才执行业务
└────────┘
```

两个关键实体都存在 `CaptchaStore` 里，全部一次性：

| 实体 | 内容 | 生命周期 |
|---|---|---|
| 验证码会话 | id、token、答案、过期时间、失败次数 | `expireSeconds`（默认 120s）；校验**通过**或失败次数耗尽后**立即删除** |
| ticket | 随机字符串 → 过期时间戳 | `ticketExpireSeconds`（默认 300s）；被业务接口**消费后立即删除** |

---

## 2. 生成阶段：答案只存服务端

`CaptchaEngine.generate()` 的关键点：

1. 按 `allow-types` 随机（或指定）选一种验证方式，交给对应生成器出题；
2. **答案（`CaptchaAnswer`）只写进服务端会话，不下发给前端**。前端拿到的只有：
   - `id`：32 位 UUID（去掉连字符），会话标识；
   - `token`：32 位 UUID + 当前纳秒时间戳的 hex 拼接，防串号提交；
   - 背景图 / 缺口图 / 提示文字等**展示信息**。
3. 会话带过期时间，到期即失效（内存 Store 由后台清扫线程清理，Redis 由 TTL 自动过期）。

> 答案不落前端，意味着攻击者无法从响应里"读"出正确答案，只能"解图"或"猜"。

---

## 3. 校验逻辑（三种验证方式）

`verify(VerifyInput)` 统一走四道前置检查，再按会话类型分派：

### 3.1 通用前置检查（任何方式都过一遍）

| 顺序 | 检查 | 失败错误码 |
|---|---|---|
| 1 | 会话存在且未过期（`store.find(id)`） | 4002 |
| 2 | `token` 与会话中保存的完全一致 | 4002 |
| 3 | 提交的验证方式与会话类型一致（可选） | 4001 |
| 4 | 操作时长 ≥ `min-duration-ms`（可配，默认 0 关闭）——过快判定为脚本 | 判失败 |

校验**失败**会 `increaseAttempts`：失败次数达到 `max-attempts` 后会话立即销毁，必须重新获取。这把"逐像素暴力尝试"的窗口限制在个位数次。

### 3.2 滑块（slider）

```
Math.abs(用户提交的x - 服务端缺口targetX) ≤ slider.tolerance（默认 8px）
```

- 用户拖动滑块，前端只提交**一个横坐标** `x`；
- 缺口位置 `targetX` 是生成时随机放置、只存在服务端的；
- 容差内即通过，通过后会话立刻删除。

### 3.3 文字点选（click）

```
按顺序逐一比较：hypot(点击坐标, 目标坐标) ≤ click.tolerance（默认 22px）
```

- 生成时从字符池（GB2312 一级字库前 2000 字 / 拉丁字母池）随机抽字，画面上可展示比必点数更多的字（`display-count > word-count`，多出来的是**干扰项**）；
- 服务端答案只保存**必点字**的坐标序列（已随机打乱），校验要求：点数必须与必点数一致，且**按顺序**逐个落在容差圆内；
- 干扰项不参与校验——点在干扰项上不算错，但顺序错了就算失败；
- 干扰项的意义是提高 OCR/图像识别的干扰难度。

### 3.4 旋转（rotate）

```
circularDiff(用户提交角度, 服务端targetAngle) ≤ rotate.tolerance（默认 8°）
```

- `circularDiff` 是环形最小差值（0~180°），所以 359° 和 1° 算等价；
- 内圈图片被随机旋转了 `targetAngle`，用户转到正向后提交角度。

### 3.5 通过后的动作

```java
if (passed) {
    store.remove(id);                              // 会话立刻销毁 → 无法重放
    String ticket = store.issueTicket(...);        // 签发一次性 ticket
    return VerifyResult.pass(ticket);
}
```

---

## 4. Ticket 的生成与消费

### 4.1 生成

```java
// MemoryCaptchaStore
String ticket = UUID.randomUUID().toString().replace("-", "");   // 128 位随机
tickets.put(ticket, System.currentTimeMillis() + ttl * 1000L);
```

- ticket 就是 **128 位随机数（122 位熵）**，与任何用户输入、图像内容无关；
- 有效期 `ticket-expire-seconds` 默认 **300 秒**；
- Redis / Redisson 实现同样：`SET {prefix}:ticket:{ticket} 1 EX ttl`。

### 4.2 消费（一次性语义）

| Store | 消费方式 | 原子性 |
|---|---|---|
| 内存 | `Map.remove(ticket)` 并校验过期时间 | ConcurrentHashMap 保证 |
| RedisTemplate | **`GETDEL`**（Redis ≥ 6.2） | 单命令原子 |
| Redisson | `RBucket.getAndDelete()` | 单命令原子 |

`GETDEL` 意味着**并发重放同一 ticket 只有一个请求能成功**，不存在"消费和校验之间被抢跑"的竞态。

### 4.3 消费入口

- 自动方式：开启 `smart-captcha.interceptor.enabled=true` 后，`@RequireCaptcha` 拦截器从 `X-Captcha-Ticket` 头（或 `captchaTicket` 参数）取 ticket，校验失败抛 `CaptchaTicketException`（HTTP 403 / code 4004）；
- 手动方式：业务代码里 `captcha.consumeTicket(ticket)`，返回 `false` 即拒绝。

> 接口传输为明文 JSON，机密性由部署层保障——生产环境请全程 HTTPS。报文加密与否不影响本节的任何结论：ticket 的安全性来自服务端随机生成 + 查表 + 一次性消费，而非传输层。

---

## 5. 能否被伪造？——逐条分析

### 5.1 伪造 ticket：不可行

| 攻击路径 | 结论 |
|---|---|
| 猜测 ticket 值 | 122 位熵，随机猜中概率约 1/5.3×10³⁶，忽略不计 |
| 自己造一个 ticket 直接调业务接口 | ticket 必须**在 Store 里存在且未过期**才有效；服务端从不校验 ticket 的格式/签名，只查表——造得再像，表里没有就是 4004 |
| 重放已用过的 ticket | 消费即删除（GETDEL），第二次就是 4004 |
| 过期后使用 | 消费时校验过期时间 / Redis TTL 自动过期 |
| 提前刷一堆 ticket 囤着 | 每个 ticket 都必须先真实通过一次行为校验，刷 ticket 的成本 = 过验证码的成本，且 300s 内不用就作废 |

**结论：ticket 本身无法伪造。** 它的安全性不依赖签名或加密，而依赖"服务器随机生成 + 服务器端查表 + 一次性消费"这个模型——类似 session id，只要随机源和存储不被攻破就是安全的。

### 5.2 伪造校验请求（跳过行为直接过 verify）：绕不过答案，但可以"解题"

| 攻击路径 | 现状 |
|---|---|
| 不带/猜错 token | 4002，且不消耗失败次数也无法通过 |
| 暴力枚举答案 | 每个会话最多 `max-attempts` 次失败即销毁，重新取题又要重新解题 |
| 重放一次成功的 verify | 通过时会话已删除，`store.find` 返回空 |
| **OCR/CV 自动识图**（滑块缺口检测、点选文字识别、旋转角度估计） | **这是真正威胁**。行为验证码是概率防御：程序化识别成功率受背景复杂度、干扰项数量、tolerance 松紧影响，但永远不是 0。它提高攻击成本，不提供绝对保证 |

### 5.3 已知弱点与加固建议

| 弱点 | 说明 | 建议 |
|---|---|---|
| **ticket 不绑定身份** | 任何持有 ticket 的人（同一 IP 换设备、或把 ticket 卖给"打码农场"下游）都能消费 | 在业务侧把 verify 时的 IP/uid 与 ticket 关联（可扩展 `issueTicket` 存 hash），消费时比对 |
| ticket 与会话无关联 | verify 和 consume 可以发生在不同客户端 | 同上 |
| `/generate` 无频率限制 | 攻击者可大量取题批量交给打码平台 | 网关层按 IP 限流 |
| `min-duration-ms` 默认关闭 | 无行为轨迹分析 | 生产环境建议开（如 500ms）；需要更强可扩展 `VerifyInput` 增加轨迹并自行校验 |
| 默认容差较宽松 | tolerance 越大越好过 | 安全敏感场景调小 tolerance、调大 `display-count` 干扰项 |
| 单机内存 Store 不共享 | 多实例部署时 A 实例签发的 ticket B 实例查不到 | 集群务必切 `redis` / `redisson` |

### 5.4 结论

- **"伪造 ticket / 伪造通过结果"在协议层面不可行**：答案与 ticket 全部存在服务端，验证即删除，随机性来自 `UUID.randomUUID()`（SecureRandom）。
- **"自动化解题"是概率层面的威胁**：任何行为验证码（包括商业产品）都无法完全阻止 OCR/CV + 打码平台，本项目的定位是把攻击成本抬到不划算；需要更强保证时，叠加限流、绑定身份、轨迹分析等外层手段。

---

## 6. 相关配置速查

```yaml
smart-captcha:
  expire-seconds: 120            # 验证码会话有效期
  ticket-expire-seconds: 300     # ticket 有效期
  max-attempts: 3                # 失败次数上限，超过即销毁会话
  min-duration-ms: 0             # >0 拒绝过快提交（反脚本）
  slider: { tolerance: 8 }       # 滑块容差 px
  click:
    tolerance: 22                # 点选容差 px
    word-count: 3                # 需要点击的字数
    display-count: 0             # 画面展示字数（>word-count 时多出的为干扰项，0=同点击数）
  rotate: { tolerance: 8 }       # 旋转容差 °
  interceptor:
    enabled: false               # 拦截器默认关闭；开启后 @RequireCaptcha 生效
```

---

*实现代码：`CaptchaEngine#verify`、`*CaptchaGenerator`、`CaptchaStore` 及其 memory/redis/redisson 实现；测试覆盖见 README「测试分布」章节。*
