package cn.localtool.smartcaptcha.spring.autoconfigure;

import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.core.generator.CaptchaGenerator;
import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import cn.localtool.smartcaptcha.core.support.BackgroundFactory;
import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 装配验证码内核引擎：把 {@link SmartCaptchaProperties} 与 {@link CaptchaStore} 交给 {@link CaptchaEngine}。
 *
 * <p>容器里所有 {@link CaptchaGenerator} Bean 都会被自动收集，用自定义实现覆盖某种验证方式
 * 只需要把它注册成 Bean，不必改配置。</p>
 */
@AutoConfiguration(after = {
        MemoryCaptchaStoreAutoConfiguration.class,
        RedisCaptchaStoreAutoConfiguration.class,
        RedissonCaptchaStoreAutoConfiguration.class
})
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE - 100)
@EnableConfigurationProperties(SmartCaptchaProperties.class)
public class SmartCaptchaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CaptchaEngine captchaEngine(SmartCaptchaProperties properties,
                                       CaptchaStore captchaStore,
                                       ObjectProvider<CaptchaGenerator> generators,
                                       ObjectProvider<BackgroundFactory> backgrounds) {
        // 用户自己注册 BackgroundFactory Bean 时优先使用，否则按 smart-captcha.background.* 构建
        BackgroundFactory backgroundFactory = backgrounds.getIfAvailable(
                () -> new BackgroundFactory(properties.getBackground()));
        CaptchaEngine.Builder builder = CaptchaEngine.builder(properties)
                .store(captchaStore)
                .backgroundFactory(backgroundFactory);
        generators.orderedStream().forEach(builder::generator);
        return builder.build();
    }
}
