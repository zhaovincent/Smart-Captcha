package cn.localtool.smartcaptcha.spring.web;

import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接入方没有写任何 @RestControllerAdvice 时，starter 也必须给出干净的 403，
 * 而不是 500 或 200。这里用 standalone MockMvc（只有默认异常解析器）验证。
 */
class CaptchaTicketStatusTests {

    @RestController
    static class GuardedController {
        @RequireCaptcha
        @PostMapping("/login")
        public String login() {
            return "should-not-run";
        }
    }

    private MockMvc mockMvc() {
        SmartCaptchaProperties properties = new SmartCaptchaProperties();
        CaptchaInterceptor interceptor = new CaptchaInterceptor(new CaptchaEngine(properties), properties);
        return MockMvcBuilders.standaloneSetup(new GuardedController())
                .addInterceptors(interceptor)
                .build();
    }

    @Test
    void missingTicketIsForbiddenEvenWithoutAdvice() throws Exception {
        mockMvc().perform(post("/login")).andExpect(status().isForbidden());
    }

    @Test
    void bogusTicketIsForbiddenEvenWithoutAdvice() throws Exception {
        mockMvc().perform(post("/login").header("X-Captcha-Ticket", "nope"))
                .andExpect(status().isForbidden());
    }
}
