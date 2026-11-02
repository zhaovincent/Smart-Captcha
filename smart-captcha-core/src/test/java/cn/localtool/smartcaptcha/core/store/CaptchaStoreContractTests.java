package cn.localtool.smartcaptcha.core.store;

import cn.localtool.smartcaptcha.core.CaptchaAnswer;
import cn.localtool.smartcaptcha.core.CaptchaSession;
import cn.localtool.smartcaptcha.core.CaptchaType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存储契约测试：同一组用例在 memory / redis / redisson（以及自定义实现）上分别跑一遍，
 * 保证切换存储后行为一致。
 *
 * <p>子类只需提供当前被测实现：{@link #store()}。</p>
 */
public abstract class CaptchaStoreContractTests {

    /** 被测实现。 */
    protected abstract CaptchaStore store();

    protected String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    protected CaptchaSession newSession(String id, long expireAt) {
        return new CaptchaSession(id, "token-" + id, CaptchaType.CLICK,
                CaptchaAnswer.click(List.of(new double[]{12.5, 30.0}, new double[]{80.0, 60.5})),
                expireAt);
    }

    @Test
    void shouldSaveAndFindSession() {
        String id = randomId();
        store().save(newSession(id, System.currentTimeMillis() + 60_000));
        try {
            CaptchaSession found = store().find(id).orElseThrow();
            assertEquals("token-" + id, found.getToken());
            assertEquals(CaptchaType.CLICK, found.getType());
            assertNotNull(found.getAnswer());
            assertEquals(2, found.getAnswer().points().size());
            assertEquals(12.5, found.getAnswer().points().get(0)[0], 1e-9);
            assertEquals(60.5, found.getAnswer().points().get(1)[1], 1e-9);
            assertEquals(0, found.getAttempts());
        } finally {
            store().remove(id);
        }
    }

    @Test
    void shouldIncreaseAttempts() {
        String id = randomId();
        store().save(newSession(id, System.currentTimeMillis() + 60_000));
        try {
            assertEquals(1, store().increaseAttempts(id));
            assertEquals(2, store().increaseAttempts(id));
            assertEquals(2, store().find(id).orElseThrow().getAttempts());
        } finally {
            store().remove(id);
        }
        // 会话不存在时返回 0，便于上层判断"已过期"
        assertEquals(0, store().increaseAttempts(id));
    }

    @Test
    void shouldRemoveSession() {
        String id = randomId();
        store().save(newSession(id, System.currentTimeMillis() + 60_000));
        store().remove(id);
        assertEquals(Optional.empty(), store().find(id));
    }

    @Test
    void shouldIgnoreExpiredSession() {
        String id = randomId();
        store().save(newSession(id, System.currentTimeMillis() - 1_000));
        try {
            assertTrue(store().find(id).isEmpty());
        } finally {
            store().remove(id);
        }
    }

    @Test
    void shouldIssueSingleUseTicket() {
        String ticket = store().issueTicket(30);
        assertNotNull(ticket);
        assertTrue(store().consumeTicket(ticket));
        assertFalse(store().consumeTicket(ticket));
        assertFalse(store().consumeTicket("not-a-ticket"));
        assertFalse(store().consumeTicket(null));
        assertFalse(store().consumeTicket("  "));
    }

    @Test
    void shouldExpireTicket() throws InterruptedException {
        String ticket = store().issueTicket(1);
        Thread.sleep(1_200);
        assertFalse(store().consumeTicket(ticket));
    }

    @Test
    void shouldReportStoreType() {
        assertNotNull(store().type());
        assertFalse(store().type().isBlank());
    }
}
