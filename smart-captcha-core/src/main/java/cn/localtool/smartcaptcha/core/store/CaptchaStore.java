package cn.localtool.smartcaptcha.core.store;

import cn.localtool.smartcaptcha.core.CaptchaSession;

import java.util.Optional;

/**
 * 验证码会话仓库 SPI —— 内核唯一的可插拔边界。
 *
 * <p>内核自带 {@link MemoryCaptchaStore}（单机内存，零依赖）；多实例部署时换成 Redis /
 * Redisson 实现即可，它们位于 {@code smart-captcha-spring-boot-starter} 模块，
 * 也可以完全自己实现（数据库、memcached、公司内部缓存……）。</p>
 *
 * <p>验证码是一次性的：校验通过或尝试次数耗尽后立即失效。ticket 同为一次性，由业务侧消费。</p>
 */
public interface CaptchaStore {

    /** 保存一张新验证码（覆盖同 id 的旧数据）。 */
    void save(CaptchaSession session);

    /** 取出会话，过期视为不存在。 */
    Optional<CaptchaSession> find(String id);

    /** 累加失败次数，返回累计后的次数；会话不存在时返回 0。 */
    int increaseAttempts(String id);

    /** 删除会话（验证通过或次数耗尽）。 */
    void remove(String id);

    /** 校验通过后签发一次性 ticket。 */
    String issueTicket(int ttlSeconds);

    /** 业务侧消费 ticket（一次性，消费后不可再用）。 */
    boolean consumeTicket(String ticket);

    /** 当前会话数量，仅用于监控与日志（大 key 场景慎用）。 */
    int sessionCount();

    /** 实现标识：memory / redis / redisson / 自定义，用于日志与 /config 展示。 */
    String type();
}
