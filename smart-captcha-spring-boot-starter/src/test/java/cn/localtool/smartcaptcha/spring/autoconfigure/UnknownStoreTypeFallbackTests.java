package cn.localtool.smartcaptcha.spring.autoconfigure;

import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 未知的 store.type 不能让应用启动失败，应回退到内存实现。 */
@SpringBootTest(properties = "smart-captcha.store.type=kafka")
class UnknownStoreTypeFallbackTests {

    @Autowired
    private CaptchaStore store;

    @Test
    void shouldFallBackToMemory() {
        assertEquals("memory", store.type());
    }
}
