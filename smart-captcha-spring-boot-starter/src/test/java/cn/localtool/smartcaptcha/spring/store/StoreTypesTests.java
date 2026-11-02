package cn.localtool.smartcaptcha.spring.store;

import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 存储类型的归一化与未知值回退（纯单元测试，不起容器）。 */
class StoreTypesTests {

    @Test
    void shouldNormalizeSupportedTypes() {
        assertEquals("memory", typeOf(null));
        assertEquals("memory", typeOf(""));
        assertEquals("memory", typeOf("memory"));
        assertEquals("memory", typeOf("IN-MEMORY"));
        assertEquals("redis", typeOf("redis"));
        assertEquals("redis", typeOf("redis-template"));
        assertEquals("redis", typeOf("lettuce"));
        assertEquals("redisson", typeOf("Redisson"));
    }

    @Test
    void shouldDetectKnownTypes() {
        assertTrue(storeOf("redis").isKnownType());
        assertTrue(storeOf("redisson").isKnownType());
        assertTrue(storeOf("").isKnownType());
        assertFalse(storeOf("kafka").isKnownType());
    }

    @Test
    void unknownTypeShouldFallBackToMemory() {
        assertEquals("memory", typeOf("kafka"));
    }

    private String typeOf(String raw) {
        return storeOf(raw).normalizedType();
    }

    private SmartCaptchaProperties.Store storeOf(String raw) {
        SmartCaptchaProperties.Store store = new SmartCaptchaProperties.Store();
        store.setType(raw);
        return store;
    }
}
