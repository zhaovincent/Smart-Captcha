package cn.localtool.smartcaptcha.spring.store;

import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.core.CaptchaException;
import cn.localtool.smartcaptcha.core.VerifyInput;
import cn.localtool.smartcaptcha.core.VerifyResult;
import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引擎跑在 Redis 存储上：生成 → 校验 → 签发 ticket → 消费 的完整链路无明显差异。
 */
@SpringBootTest(properties = {
        "smart-captcha.store.type=redis",
        "spring.data.redis.host=${test.redis.host:127.0.0.1}",
        "spring.data.redis.port=${test.redis.port:6379}"
})
@ExtendWith(RedisAvailableCondition.class)
class RedisStoreCaptchaFlowTests {

    @Autowired
    private CaptchaEngine engine;

    @Autowired
    private CaptchaStore store;

    @Test
    void sliderFlowShouldPassAndIssueTicket() {
        CaptchaChallenge challenge = engine.generate("slider");
        double targetX = store.find(challenge.id()).orElseThrow().getAnswer().targetX();

        VerifyResult result = engine.verify(VerifyInput.slider(challenge.id(), challenge.token(), targetX)
                .withDuration(1500L));

        assertTrue(result.success());
        assertTrue(store.consumeTicket(result.ticket()));
        assertFalse(store.consumeTicket(result.ticket()));
    }

    @Test
    void wrongAnswersShouldLockAfterMaxAttempts() {
        CaptchaChallenge challenge = engine.generate("slider");
        String id = challenge.id();

        for (int i = 1; i <= 3; i++) {
            VerifyResult result = engine.verify(VerifyInput.slider(id, challenge.token(), -999d));
            assertFalse(result.success());
            assertEquals(3 - i, result.attemptsLeft());
        }
        // 次数耗尽后会话已从 Redis 删除
        assertTrue(store.find(id).isEmpty());
        assertThrows(CaptchaException.class,
                () -> engine.verify(VerifyInput.slider(id, challenge.token(), -999d)));
    }
}
