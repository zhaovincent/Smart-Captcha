package cn.localtool.smartcaptcha.spring.web;

import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.core.VerifyInput;
import cn.localtool.smartcaptcha.core.VerifyResult;
import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 配了 {@code smart-captcha.interceptor.enabled=true} 之后，{@code @RequireCaptcha} 才真正生效。
 */
@SpringBootTest(properties = "smart-captcha.interceptor.enabled=true")
@AutoConfigureMockMvc
class InterceptorEnabledTests {

    @Autowired
    private SmartCaptchaProperties properties;

    @Autowired
    private CaptchaEngine engine;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void propertyIsBound() {
        assertTrue(properties.getInterceptor().isEnabled());
    }

    @Test
    void annotatedEndpointIsBlockedWithoutTicket() throws Exception {
        mockMvc.perform(post("/test/guarded"))
                .andExpect(status().isForbidden());
    }

    @Test
    void annotatedEndpointPassesWithValidTicket() throws Exception {
        mockMvc.perform(post("/test/guarded").header("X-Captcha-Ticket", newTicket()))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void unannotatedEndpointIsNeverChecked() throws Exception {
        mockMvc.perform(post("/test/open"))
                .andExpect(status().isOk());
    }

    /** 走一遍滑块验证，拿一枚有效 ticket。 */
    private String newTicket() {
        CaptchaChallenge challenge = engine.generate("slider");
        double targetX = engine.store().find(challenge.id()).orElseThrow().getAnswer().targetX();
        VerifyResult result = engine.verify(VerifyInput.slider(challenge.id(), challenge.token(), targetX)
                .withDuration(1200L));
        assertTrue(result.success(), "预置验证码未通过，测试环境异常");
        return result.ticket();
    }
}
