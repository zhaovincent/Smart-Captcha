package cn.localtool.smartcaptcha.spring.autoconfigure;

import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.core.CaptchaType;
import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 默认配置（未写 store.type）应装配内存实现，并暴露可用的引擎 Bean。 */
@SpringBootTest
class DefaultAutoConfigurationTests {

    @Autowired
    private CaptchaEngine engine;

    @Autowired
    private CaptchaStore store;

    @Test
    void shouldUseMemoryStoreByDefault() {
        assertEquals("memory", store.type());
    }

    @Test
    void engineShouldExposeFourBuiltInTypes() {
        assertNotNull(engine);
        assertTrue(engine.supportedTypes().containsAll(
                List.of(CaptchaType.SLIDER, CaptchaType.CLICK, CaptchaType.ROTATE, CaptchaType.HUMAN)));
        assertEquals(4, engine.supportedTypes().size());
        assertEquals("memory", engine.publicConfig().get("store"));
    }
}
