package cn.localtool.smartcaptcha.core.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 内核默认实现：内存存储。 */
class MemoryCaptchaStoreTests extends CaptchaStoreContractTests {

    private MemoryCaptchaStore memoryStore;

    @BeforeEach
    void setUp() {
        memoryStore = new MemoryCaptchaStore();
    }

    @AfterEach
    void tearDown() {
        memoryStore.close();
    }

    @Override
    protected CaptchaStore store() {
        return memoryStore;
    }

    @Test
    void shouldReportMemoryType() {
        assertEquals("memory", memoryStore.type());
    }
}
