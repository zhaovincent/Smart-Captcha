package cn.localtool.smartcaptcha.spring.autoconfigure;

import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import cn.localtool.smartcaptcha.spring.store.RedisCaptchaStore;
import cn.localtool.smartcaptcha.spring.store.StoreTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@code smart-captcha.store.type=redis}：复用 Spring Boot 的 {@code spring.data.redis.*} 配置，
 * 通过 {@link StringRedisTemplate} 共享会话与 ticket。
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE - 500)
@ConditionalOnClass({StringRedisTemplate.class, RedisConnectionFactory.class})
@ConditionalOnStoreType(StoreTypes.REDIS)
public class RedisCaptchaStoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisCaptchaStoreAutoConfiguration.class);

    private static final String DEFAULT_PREFIX = "smart-captcha";

    @Bean
    @ConditionalOnMissingBean(CaptchaStore.class)
    public CaptchaStore captchaStore(SmartCaptchaProperties properties,
                                     ObjectProvider<StringRedisTemplate> templates,
                                     ObjectProvider<RedisConnectionFactory> factories,
                                     ObjectProvider<ObjectMapper> objectMappers) {
        String prefix = normalizePrefix(properties.getStore().getKeyPrefix());
        StringRedisTemplate template = resolveTemplate(templates, factories);
        CaptchaStore store = new RedisCaptchaStore(template, objectMappers.getIfAvailable(ObjectMapper::new), prefix);
        log.info("验证码存储：redis（key 前缀 {}）", prefix);
        return store;
    }

    /** 优先复用容器里的 StringRedisTemplate（或 Spring Boot 自动装配的那个），否则用连接工厂自建。 */
    private StringRedisTemplate resolveTemplate(ObjectProvider<StringRedisTemplate> templates,
                                                ObjectProvider<RedisConnectionFactory> factories) {
        StringRedisTemplate template = templates.getIfAvailable();
        if (template != null) {
            return template;
        }
        RedisConnectionFactory factory = factories.getIfAvailable();
        if (factory == null) {
            throw new IllegalStateException("smart-captcha.store.type=redis 需要 Redis 连接：请配置 spring.data.redis.host/port，"
                    + "或自行提供 StringRedisTemplate / RedisConnectionFactory Bean");
        }
        StringRedisTemplate created = new StringRedisTemplate(factory);
        created.afterPropertiesSet();
        return created;
    }

    private String normalizePrefix(String keyPrefix) {
        return keyPrefix == null || keyPrefix.isBlank() ? DEFAULT_PREFIX : keyPrefix.trim();
    }
}
