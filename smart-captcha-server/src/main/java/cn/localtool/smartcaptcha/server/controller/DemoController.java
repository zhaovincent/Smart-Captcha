package cn.localtool.smartcaptcha.server.controller;

import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.server.web.ApiResult;
import cn.localtool.smartcaptcha.spring.web.CaptchaInterceptor;
import cn.localtool.smartcaptcha.spring.web.CaptchaTicketException;
import cn.localtool.smartcaptcha.spring.web.RequireCaptcha;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 业务接口接入示例：演示两种"验证码通过后，提交业务数据时继续校验"的写法。
 *
 * <ul>
 *   <li>{@code POST /api/demo/login}：开启拦截器（{@code smart-captcha.interceptor.enabled=true}）时，
 *       标 {@link RequireCaptcha} 即可，ticket 进方法前已被消费；</li>
 *   <li>{@code POST /api/demo/login-manual}：不依赖拦截器，方法里自己调
 *       {@link CaptchaEngine#consumeTicket(String)}，适合只想在某个分支、某个时机校验的场景。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final CaptchaEngine engine;

    public DemoController(CaptchaEngine engine) {
        this.engine = engine;
    }

    /**
     * 模拟登录（拦截器方式）。没有有效 ticket 时请求根本进不到这个方法（拦截器返回 403 / code 4004）。
     */
    @PostMapping("/login")
    @RequireCaptcha
    public ApiResult<Map<String, Object>> login(@RequestBody(required = false) Map<String, String> body,
                                                HttpServletRequest request) {
        String username = body == null ? null : body.get("username");
        String ticket = (String) request.getAttribute(CaptchaInterceptor.TICKET_ATTRIBUTE);
        log.info("business login accepted, username={}, ticket={}", username, mask(ticket));
        return ApiResult.ok(payload(username, "登录成功：ticket 已在服务端消费，重复提交会被拒绝"));
    }

    /**
     * 模拟登录（业务自校验方式，拦截器关闭时用这个）。
     *
     * <p>ticket 依次从 {@code X-Captcha-Ticket} 请求头、{@code captchaTicket} 请求参数、
     * JSON body 的 {@code captchaTicket} 字段读取，取到后交给引擎一次性消费。</p>
     */
    @PostMapping("/login-manual")
    public ApiResult<Map<String, Object>> loginManually(@RequestBody(required = false) Map<String, String> body,
                                                        HttpServletRequest request) {
        String ticket = readTicket(body, request);
        if (!engine.consumeTicket(ticket)) {
            throw new CaptchaTicketException("请先完成人机验证（ticket 缺失、已过期或已被使用）", "X-Captcha-Ticket");
        }
        String username = body == null ? null : body.get("username");
        log.info("business login accepted (manual check), username={}, ticket={}", username, mask(ticket));
        return ApiResult.ok(payload(username, "登录成功：业务接口自行校验并消费了 ticket"));
    }

    private String readTicket(Map<String, String> body, HttpServletRequest request) {
        String ticket = request.getHeader("X-Captcha-Ticket");
        if (ticket == null || ticket.isBlank()) {
            ticket = request.getParameter("captchaTicket");
        }
        if ((ticket == null || ticket.isBlank()) && body != null) {
            ticket = body.get("captchaTicket");
        }
        return ticket;
    }

    private Map<String, Object> payload(String username, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username);
        data.put("token", UUID.randomUUID().toString().replace("-", ""));
        data.put("captchaVerified", true);
        data.put("message", message);
        return data;
    }

    private String mask(String ticket) {
        return ticket == null ? "-" : ticket.substring(0, Math.min(8, ticket.length())) + "…";
    }
}
