package cn.localtool.smartcaptcha.spring.autoconfigure;

import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import cn.localtool.smartcaptcha.spring.store.RedissonCaptchaStore;
import cn.localtool.smartcaptcha.spring.store.StoreTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * {@code smart-captcha.store.type=redisson}：使用 Redisson 客户端，
 * 连接参数来自 {@code smart-captcha.store.address/password/database}；
 * 若容器里已有 {@link RedissonClient} Bean，则直接复用。
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE - 490)
@ConditionalOnClass({RedissonClient.class, Redisson.class})
@ConditionalOnStoreType(StoreTypes.REDISSON)
public class RedissonCaptchaStoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedissonCaptchaStoreAutoConfiguration.class);

    private static final String DEFAULT_PREFIX = "smart-captcha";

    /** 容器里没有 Redisson 客户端时，按配置自建一个（由容器负责关闭）。 */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(SmartCaptchaProperties properties) {
        SmartCaptchaProperties.Store store = properties.getStore();
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress(withScheme(store.getAddress()))
                .setDatabase(Math.max(0, store.getDatabase()));
        if (store.getPassword() != null && !store.getPassword().isBlank()) {
            server.setPassword(store.getPassword());
        }
        try {
            return Redisson.create(config);
        } catch (RuntimeException e) {
            throw new IllegalStateException("初始化 Redisson 失败，请检查 smart-captcha.store.address/password/database："
                    + store.getAddress(), e);
        }
    }

    @Bean
    @ConditionalOnMissingBean(CaptchaStore.class)
    public CaptchaStore captchaStore(SmartCaptchaProperties properties,
                                     RedissonClient redissonClient,
                                     ObjectProvider<ObjectMapper> objectMappers) {
        String prefix = normalizePrefix(properties.getStore().getKeyPrefix());
        // 客户端生命周期由容器管理，store 不再重复关闭
        CaptchaStore store = new RedissonCaptchaStore(
                redissonClient, objectMappers.getIfAvailable(ObjectMapper::new), prefix, false);
        log.info("验证码存储：redisson（key 前缀 {}）", prefix);
        return store;
    }

    private String withScheme(String address) {
        String value = address == null || address.isBlank() ? "127.0.0.1:6379" : address.trim();
        if (value.startsWith("redis://") || value.startsWith("rediss://") || value.startsWith("unix://")) {
            return value;
        }
        return "redis://" + value;
    }

    private String normalizePrefix(String keyPrefix) {
        return keyPrefix == null || keyPrefix.isBlank() ? DEFAULT_PREFIX : keyPrefix.trim();
    }
}
