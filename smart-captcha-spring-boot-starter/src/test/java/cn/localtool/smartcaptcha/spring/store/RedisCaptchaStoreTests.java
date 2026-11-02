package cn.localtool.smartcaptcha.spring.store;

import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import cn.localtool.smartcaptcha.core.store.CaptchaStoreContractTests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** store.type=redis：走 Spring Data Redis 的 StringRedisTemplate，需要可连通的 Redis。 */
@SpringBootTest(properties = {
        "smart-captcha.store.type=redis",
        "spring.data.redis.host=${test.redis.host:127.0.0.1}",
        "spring.data.redis.port=${test.redis.port:6379}"
})
@ExtendWith(RedisAvailableCondition.class)
class RedisCaptchaStoreTests extends CaptchaStoreContractTests {

    @Autowired
    private CaptchaStore captchaStore;

    @Override
    protected CaptchaStore store() {
        return captchaStore;
    }

    @Test
    void shouldUseRedisTemplateStore() {
        assertEquals("redis", captchaStore.type());
    }
}
