package cn.localtool.smartcaptcha.server;

import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.core.VerifyInput;
import cn.localtool.smartcaptcha.core.VerifyResult;
import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 业务接口继续校验 ticket 的集成测试：没有 ticket 拦截、ticket 一次性、验证码接口自身不被拦截。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BusinessTicketTests {

    private static final String LOGIN = "/api/demo/login";
    private static final String LOGIN_MANUAL = "/api/demo/login-manual";
    private static final String TICKET_HEADER = "X-Captcha-Ticket";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaptchaEngine engine;

    @Autowired
    private CaptchaStore captchaStore;

    @Test
    void loginWithoutTicketShouldBeBlockedByInterceptor() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4004))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginShouldPassOnceAndRejectReplay() throws Exception {
        String ticket = passSlider();

        mockMvc.perform(post(LOGIN)
                        .header(TICKET_HEADER, ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.captchaVerified").value(true));

        // 同一个 ticket 再用一次必须失败
        mockMvc.perform(post(LOGIN)
                        .header(TICKET_HEADER, ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"123456\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    void bogusTicketShouldBeRejected() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .header(TICKET_HEADER, "not-a-real-ticket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    void captchaApiItselfShouldNotNeedTicket() throws Exception {
        mockMvc.perform(get("/api/captcha/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 拦截器关闭时业务接口自己校验的写法：没有 ticket 一样拒绝。 */
    @Test
    void manualCheckWithoutTicketShouldBeRejected() throws Exception {
        mockMvc.perform(post(LOGIN_MANUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4004));
    }

    /** 业务自校验同样是一次性消费：body 里带 ticket 通过一次，第二次被拒。 */
    @Test
    void manualCheckConsumesTicketOnce() throws Exception {
        String ticket = passSlider();
        String body = "{\"username\":\"demo\",\"captchaTicket\":\"" + ticket + "\"}";

        mockMvc.perform(post(LOGIN_MANUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.captchaVerified").value(true));

        mockMvc.perform(post(LOGIN_MANUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4004));
    }

    /** 走一遍滑块验证，拿到一枚有效 ticket。 */
    private String passSlider() {
        CaptchaChallenge challenge = engine.generate("slider");
        double targetX = captchaStore.find(challenge.id()).orElseThrow().getAnswer().targetX();
        VerifyResult result = engine.verify(VerifyInput.slider(challenge.id(), challenge.token(), targetX)
                .withDuration(1200L));
        if (!result.success() || result.ticket() == null) {
            throw new IllegalStateException("预置验证码未通过，测试环境异常");
        }
        return result.ticket();
    }
}
