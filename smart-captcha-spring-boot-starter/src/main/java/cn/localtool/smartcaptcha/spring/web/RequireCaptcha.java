package cn.localtool.smartcaptcha.spring.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在业务接口（Controller 方法或类）上，表示该接口要求请求携带有效的验证码 ticket。
 *
 * <p><b>需要先开启拦截器</b>：{@code smart-captcha.interceptor.enabled=true}（默认 false，
 * 未开启时本注解不产生任何效果，由业务接口自行校验 ticket）。</p>
 *
 * <p>由 {@link CaptchaInterceptor} 统一校验：ticket 缺失 / 过期 / 已被使用都会抛出
 * {@link CaptchaTicketException}（默认 HTTP 403），业务方法不会被执行；校验通过后 ticket
 * 立即作废（一次性）。响应体由接入方决定——starter 不自带任何返回包装。</p>
 *
 * <pre>{@code
 * @PostMapping("/login")
 * @RequireCaptcha
 * public LoginResult login(@RequestBody LoginRequest req) { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireCaptcha {

    /** 从哪个请求头读取 ticket。 */
    String header() default "X-Captcha-Ticket";

    /** 校验失败时的提示文案。 */
    String message() default "请先完成人机验证";
}
