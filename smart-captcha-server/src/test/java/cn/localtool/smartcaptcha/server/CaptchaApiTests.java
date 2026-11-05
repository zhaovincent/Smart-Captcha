package cn.localtool.smartcaptcha.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** REST 接口契约：配置、生成、校验、参数错误。 */
@SpringBootTest
@AutoConfigureMockMvc
class CaptchaApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void configShouldListFourTypes() throws Exception {
        mockMvc.perform(get("/api/captcha/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.types.length()").value(4))
                .andExpect(jsonPath("$.data.store").value("memory"))
                .andExpect(jsonPath("$.data.expireIn").value(120));
    }

    @Test
    void generateShouldReturnChallengeWithoutAnswer() throws Exception {
        mockMvc.perform(post("/api/captcha/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"slider\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.type").value("slider"))
                .andExpect(jsonPath("$.data.image").isNotEmpty())
                .andExpect(jsonPath("$.data.slider.piece").isNotEmpty())
                .andExpect(jsonPath("$.data.answer").doesNotExist());
    }

    @Test
    void generateByGetShouldHonourClickCharset() throws Exception {
        mockMvc.perform(get("/api/captcha/generate").param("type", "click").param("clickCharset", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("click"))
                .andExpect(jsonPath("$.data.click.charset").value("en"));
    }

    @Test
    void verifyWithWrongAnswerShouldReportRemainingAttempts() throws Exception {
        String body = mockMvc.perform(post("/api/captcha/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"slider\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id", data.path("id").asText());
        payload.put("token", data.path("token").asText());
        payload.put("type", "slider");
        payload.put("x", -999);
        payload.put("duration", 1200);

        mockMvc.perform(post("/api/captcha/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.attemptsLeft").value(2));
    }

    @Test
    void unknownTypeShouldReturnBadRequestCode() throws Exception {
        mockMvc.perform(post("/api/captcha/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"voice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void verifyWithUnknownIdShouldReturnExpiredCode() throws Exception {
        mockMvc.perform(post("/api/captcha/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"nope\",\"token\":\"nope\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    void ticketVerifyShouldConsumeOnFirstUse() throws Exception {
        mockMvc.perform(post("/api/captcha/ticket/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"unknown-ticket\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false));
    }
}
