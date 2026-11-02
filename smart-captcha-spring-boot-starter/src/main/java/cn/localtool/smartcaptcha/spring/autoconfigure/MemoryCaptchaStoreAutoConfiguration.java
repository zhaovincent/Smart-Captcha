package cn.localtool.smartcaptcha.spring.autoconfigure;

import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import cn.localtool.smartcaptcha.core.store.MemoryCaptchaStore;
import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import cn.localtool.smartcaptcha.spring.store.StoreTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 内存存储兜底装配。
 *
 * <p>排在 Redis / Redisson 之后，因此：</p>
 * <ul>
 *     <li>{@code store.type=memory}（默认）→ 用内存；</li>
 *     <li>{@code store.type=redis/redisson} 且依赖与配置就绪 → 前面已注册，这里自动跳过；</li>
 *     <li>{@code store.type} 写了未知值，或写了 redis 但没引 Redis 依赖 → 回落内存并打印警告，
 *         保证应用不因一个配置项启动失败。</li>
 * </ul>
 */
@AutoConfiguration(after = {RedisCaptchaStoreAutoConfiguration.class, RedissonCaptchaStoreAutoConfiguration.class})
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE - 200)
public class MemoryCaptchaStoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MemoryCaptchaStoreAutoConfiguration.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CaptchaStore.class)
    public MemoryCaptchaStore captchaStore(SmartCaptchaProperties properties) {
        String configured = properties.getStore().getType();
        String normalized = properties.getStore().normalizedType();
        if (!StoreTypes.MEMORY.equals(normalized)) {
            log.warn("smart-captcha.store.type={} 不可用（缺少对应依赖或配置），已回退为内存存储", configured);
        } else if (!properties.getStore().isKnownType()) {
            log.warn("未知的 smart-captcha.store.type「{}」，已回退为内存存储", configured);
        }
        log.info("验证码存储：memory（单实例）；多实例部署请切换 store.type 为 redis 或 redisson");
        return new MemoryCaptchaStore();
    }
}
