package cn.localtool.smartcaptcha.spring.autoconfigure;

import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import cn.localtool.smartcaptcha.spring.web.CaptchaInterceptor;
import cn.localtool.smartcaptcha.spring.web.SmartCaptchaWebMvcConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 环境下的装配：{@link CaptchaInterceptor} + 拦截器/CORS 注册。
 *
 * <p>非 Web 应用（纯 Kafka consumer、批处理等）只用到 {@link CaptchaEngine}，
 * 这里整体不生效，也就不会引入任何 servlet 依赖要求。</p>
 */
@AutoConfiguration(after = SmartCaptchaAutoConfiguration.class)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE - 50)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({DispatcherServlet.class, WebMvcConfigurer.class})
@EnableConfigurationProperties(SmartCaptchaProperties.class)
public class SmartCaptchaWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CaptchaInterceptor captchaInterceptor(CaptchaEngine engine, SmartCaptchaProperties properties) {
        return new CaptchaInterceptor(engine, properties);
    }

    @Bean
    public SmartCaptchaWebMvcConfigurer smartCaptchaWebMvcConfigurer(CaptchaInterceptor captchaInterceptor,
                                                                    SmartCaptchaProperties properties) {
        return new SmartCaptchaWebMvcConfigurer(captchaInterceptor, properties);
    }
}
