package cn.localtool.smartcaptcha.spring.web;

import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 默认不开启拦截器：标了 {@code @RequireCaptcha} 的接口照常放行，由业务自己校验 ticket。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InterceptorDisabledByDefaultTests {

    @Autowired
    private SmartCaptchaProperties properties;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void propertiesDefaultToDisabled() {
        assertNotNull(properties.getInterceptor());
        assertFalse(properties.getInterceptor().isEnabled());
    }

    @Test
    void annotatedEndpointRunsWithoutTicket() throws Exception {
        mockMvc.perform(post("/test/guarded"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}
