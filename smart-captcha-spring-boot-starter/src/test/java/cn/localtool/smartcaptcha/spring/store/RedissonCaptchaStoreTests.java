package cn.localtool.smartcaptcha.spring.store;

import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import cn.localtool.smartcaptcha.core.store.CaptchaStoreContractTests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** store.type=redisson：走 Redisson 客户端，需要可连通的 Redis。 */
@SpringBootTest(properties = {
        "smart-captcha.store.type=redisson",
        "smart-captcha.store.address=redis://${test.redis.host:127.0.0.1}:${test.redis.port:6379}"
})
@ExtendWith(RedisAvailableCondition.class)
class RedissonCaptchaStoreTests extends CaptchaStoreContractTests {

    @Autowired
    private CaptchaStore captchaStore;

    @Override
    protected CaptchaStore store() {
        return captchaStore;
    }

    @Test
    void shouldUseRedissonStore() {
        assertEquals("redisson", captchaStore.type());
    }
}
