package cn.localtool.smartcaptcha.spring.web;

import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 按 {@code smart-captcha.interceptor.enabled} 决定是否注册 {@link CaptchaInterceptor}，
 * 并按 {@code smart-captcha.allowed-origins} 放开 CORS。
 *
 * <p>拦截器默认不注册（业务接口自己校验 ticket）；开启后挂 {@code /**}，
 * 但只有带 {@link RequireCaptcha} 的 HandlerMethod 才真正生效，所以不需要为验证码
 * 自身接口维护排除列表。</p>
 */
public class SmartCaptchaWebMvcConfigurer implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SmartCaptchaWebMvcConfigurer.class);

    private final CaptchaInterceptor interceptor;
    private final SmartCaptchaProperties properties;

    public SmartCaptchaWebMvcConfigurer(CaptchaInterceptor interceptor, SmartCaptchaProperties properties) {
        this.interceptor = interceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!properties.getInterceptor().isEnabled()) {
            // 默认关闭：宁可少一层隐式拦截，也不让接入方在不知情的情况下被 403
            log.debug("smart-captcha interceptor disabled, business endpoints must verify the ticket themselves");
            return;
        }
        registry.addInterceptor(interceptor).addPathPatterns("/**");
        log.info("smart-captcha interceptor enabled, endpoints annotated with @RequireCaptcha are guarded");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = properties.getAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
