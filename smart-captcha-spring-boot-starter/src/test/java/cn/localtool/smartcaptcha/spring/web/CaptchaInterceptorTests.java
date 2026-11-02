package cn.localtool.smartcaptcha.spring.web;

import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.core.VerifyInput;
import cn.localtool.smartcaptcha.core.VerifyResult;
import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拦截器只做判断、失败抛异常：不写响应体，也不依赖任何返回包装类。
 */
class CaptchaInterceptorTests {

    private final SmartCaptchaProperties properties = new SmartCaptchaProperties();
    private final CaptchaEngine engine = new CaptchaEngine(properties);
    private final CaptchaInterceptor interceptor = new CaptchaInterceptor(engine, properties);

    /** 被保护的"业务"接口。 */
    static class Guarded {
        @RequireCaptcha
        @PostMapping("/login")
        public String login() {
            return "ok";
        }

        @RequireCaptcha(header = "X-Token", message = "自定义提示")
        @PostMapping("/custom")
        public String custom() {
            return "ok";
        }

        @PostMapping("/open")
        public String open() {
            return "ok";
        }
    }

    private HandlerMethod handler(String method) throws NoSuchMethodException {
        Guarded bean = new Guarded();
        return new HandlerMethod(bean, Guarded.class.getMethod(method));
    }

    private boolean preHandle(String method, MockHttpServletRequest request) throws NoSuchMethodException {
        return interceptor.preHandle(request, new MockHttpServletResponse(), handler(method));
    }

    @Test
    void missingTicketThrowsInsteadOfWritingResponse() throws Exception {
        CaptchaTicketException e = assertThrows(CaptchaTicketException.class,
                () -> preHandle("login", new MockHttpServletRequest("POST", "/login")));

        assertTrue(e.getMessage().startsWith("请先完成人机验证"), e.getMessage());
        assertTrue(e.getMessage().contains("ticket 缺失、已过期或已被使用"));
        assertEquals("X-Captcha-Ticket", e.getHeader());
        assertEquals(4004, CaptchaTicketException.CODE);
    }

    @Test
    void exceptionCarriesForbiddenStatus() {
        // Spring 用 AnnotatedElementUtils 解析别名属性（value ←→ code），裸反射读到的是默认值
        ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(
                CaptchaTicketException.class, ResponseStatus.class);
        assertNotNull(status, "缺少 @ResponseStatus 时接入方忘了写 advice 就只能拿到 500");
        assertEquals(HttpStatus.FORBIDDEN, status.code());
    }

    @Test
    void bogusTicketThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addHeader("X-Captcha-Ticket", "not-a-real-ticket");
        assertThrows(CaptchaTicketException.class, () -> preHandle("login", request));
    }

    @Test
    void customHeaderAndMessageAreUsed() throws Exception {
        CaptchaTicketException e = assertThrows(CaptchaTicketException.class,
                () -> preHandle("custom", new MockHttpServletRequest("POST", "/custom")));
        assertEquals("X-Token", e.getHeader());
        assertTrue(e.getMessage().startsWith("自定义提示"), e.getMessage());
    }

    @Test
    void unannotatedHandlerIsIgnored() throws Exception {
        assertTrue(preHandle("open", new MockHttpServletRequest("POST", "/open")));
    }

    @Test
    void disabledCaptchaSkipsEverything() throws Exception {
        properties.setEnabled(false);
        assertTrue(preHandle("login", new MockHttpServletRequest("POST", "/login")));
    }

    @Test
    void validTicketPassesOnceAndIsExposedAsAttribute() throws Exception {
        String ticket = newTicket();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addHeader("X-Captcha-Ticket", ticket);
        assertTrue(preHandle("login", request));
        assertEquals(ticket, request.getAttribute(CaptchaInterceptor.TICKET_ATTRIBUTE));

        // 一次性：同一枚 ticket 再用一次必须被拒
        MockHttpServletRequest replay = new MockHttpServletRequest("POST", "/login");
        replay.addHeader("X-Captcha-Ticket", ticket);
        assertThrows(CaptchaTicketException.class, () -> preHandle("login", replay));
    }

    @Test
    void ticketCanComeFromRequestParameter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("captchaTicket", newTicket());
        assertTrue(preHandle("login", request));
    }

    @Test
    void staticResourceHandlerIsIgnored() {
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
    }

    /** 走一遍滑块验证，拿一枚有效 ticket。 */
    private String newTicket() {
        CaptchaChallenge challenge = engine.generate("slider");
        double targetX = engine.store().find(challenge.id()).orElseThrow().getAnswer().targetX();
        VerifyResult result = engine.verify(VerifyInput.slider(challenge.id(), challenge.token(), targetX)
                .withDuration(1200L));
        assertTrue(result.success(), "预置验证码未通过，测试环境异常");
        assertFalse(result.ticket().isBlank());
        return result.ticket();
    }
}
