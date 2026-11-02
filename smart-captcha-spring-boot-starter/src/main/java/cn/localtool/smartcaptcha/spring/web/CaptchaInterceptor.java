package cn.localtool.smartcaptcha.spring.web;

import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 业务接口的验证码拦截器：只处理标了 {@link RequireCaptcha} 的接口。
 *
 * <p><b>默认不启用。</b>需要显式配置 {@code smart-captcha.interceptor.enabled=true}
 * 才会注册到 Spring MVC；不开启时业务接口自己从请求里取 ticket 并调用
 * {@code CaptchaEngine#consumeTicket(String)} 校验。{@code smart-captcha.enabled=false}
 * 时即使注册了也一律放行。</p>
 *
 * <p>拦截顺序：先校验 ticket（一次性消费），再进入业务方法。
 * 也就是说业务方法里拿到的请求一定已经通过人机验证；反过来，就算业务逻辑随后失败
 * （例如密码错误），ticket 也已经被消费，用户需要重新过一次验证码——这是常见取舍，
 * 因为"先放行再校验"会让 ticket 能被反复尝试。</p>
 *
 * <p>校验不通过时抛 {@link CaptchaTicketException}，由接入方的 {@code @RestControllerAdvice}
 * 决定响应体（默认 {@code @ResponseStatus} 保证 HTTP 403）。starter 不写任何响应内容。</p>
 */
public class CaptchaInterceptor implements HandlerInterceptor {

    /** 校验通过后可通过该 request 属性拿到本次消费掉的 ticket（审计、风控埋点用）。 */
    public static final String TICKET_ATTRIBUTE = "smart-captcha.ticket";

    private static final Logger log = LoggerFactory.getLogger(CaptchaInterceptor.class);

    private final CaptchaEngine engine;
    private final SmartCaptchaProperties properties;

    public CaptchaInterceptor(CaptchaEngine engine, SmartCaptchaProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true; // 静态资源等，直接放过
        }

        RequireCaptcha require = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequireCaptcha.class);
        if (require == null) {
            require = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireCaptcha.class);
        }
        if (require == null || !properties.isEnabled()) {
            return true;
        }

        String ticket = request.getHeader(require.header());
        if (ticket == null || ticket.isBlank()) {
            ticket = request.getParameter("captchaTicket"); // 表单/不便加头的场景
        }

        if (engine.consumeTicket(ticket)) {
            request.setAttribute(TICKET_ATTRIBUTE, ticket);
            return true;
        }

        log.debug("captcha ticket rejected, uri={}", request.getRequestURI());
        throw new CaptchaTicketException(
                require.message() + "（ticket 缺失、已过期或已被使用）", require.header());
    }
}
