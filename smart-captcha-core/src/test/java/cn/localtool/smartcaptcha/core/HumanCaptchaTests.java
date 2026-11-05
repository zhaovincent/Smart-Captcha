package cn.localtool.smartcaptcha.core;

import cn.localtool.smartcaptcha.core.store.MemoryCaptchaStore;
import cn.localtool.smartcaptcha.core.support.ProofOfWork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一键人机验证的内核测试：PoW 验算、耗时门槛、交互痕迹、一次性与防重放。
 */
class HumanCaptchaTests {

    private static final int DIFFICULTY = 10; // 平均 1024 次哈希，测试跑得动

    private MemoryCaptchaStore store;
    private CaptchaEngine engine;

    @BeforeEach
    void setUp() {
        CaptchaConfig config = new CaptchaConfig();
        config.setAllowTypes(List.of("human"));
        config.getHuman().setDifficulty(DIFFICULTY);
        // 默认关闭随机等待，专测 PoW / 交互痕迹；耗时门槛在各自的用例里单独配置
        config.getHuman().setDelayMinMs(0);
        config.getHuman().setDelayMaxMs(0);
        config.getHuman().setMinMoves(2);
        config.getHuman().setRequireTrace(true);
        store = new MemoryCaptchaStore();
        engine = new CaptchaEngine(config, store);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void generateShouldReturnPowChallengeWithoutImage() {
        CaptchaChallenge challenge = engine.generate("human", null, null);

        assertEquals("human", challenge.type());
        assertNotNull(challenge.human());
        assertEquals("SHA-256", challenge.human().algorithm());
        assertEquals(DIFFICULTY, challenge.human().difficulty());
        assertEquals(32, challenge.human().salt().length());
        assertTrue(challenge.human().requireTrace());
        // 人机验证不出图：不生成背景、不做 PNG 编码
        assertNull(challenge.image());
        assertNull(challenge.slider());
        assertNull(challenge.click());
        assertNull(challenge.rotate());
    }

    @Test
    void shouldPassWithSolvedNonceAndInteraction() {
        CaptchaChallenge challenge = engine.generate("human", null, null);
        long nonce = solve(challenge);

        VerifyResult result = engine.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce,
                HumanTrace.of(12, 1, 0, 320, 0.82, "mouse")).withDuration(1500L));

        assertTrue(result.success());
        assertNotNull(result.ticket());
        assertTrue(store.consumeTicket(result.ticket()));
    }

    @Test
    void shouldPassWithKeyboardOnlyUser() {
        CaptchaChallenge challenge = engine.generate("human", null, null);
        long nonce = solve(challenge);

        // 无鼠标、只用键盘（无障碍场景）：keydown 也是真实交互
        VerifyResult result = engine.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce,
                HumanTrace.of(0, 0, 3, 0, 0, "")).withDuration(800L));

        assertTrue(result.success());
    }

    @Test
    void shouldPassWithTouchTap() {
        CaptchaChallenge challenge = engine.generate("human", null, null);
        long nonce = solve(challenge);

        // 手指点一下没有移动轨迹，只有一次 pointerdown
        VerifyResult result = engine.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce,
                HumanTrace.of(0, 1, 0, 0, 0, "touch")).withDuration(900L));

        assertTrue(result.success());
    }

    @Test
    void shouldFailWithWrongNonce() {
        CaptchaChallenge challenge = engine.generate("human", null, null);
        long wrong = (solve(challenge) + 1) % 100000;

        VerifyResult result = engine.verify(VerifyInput.human(challenge.id(), challenge.token(), wrong,
                HumanTrace.of(12, 1, 0, 320, 0.82, "mouse")).withDuration(1500L));

        assertFalse(result.success());
        assertNotNull(result.attemptsLeft());
        assertTrue(result.message().contains("工作量证明"));
    }

    @Test
    void shouldFailWhenProofMissing() {
        CaptchaChallenge challenge = engine.generate("human", null, null);

        VerifyResult result = engine.verify(VerifyInput.human(challenge.id(), challenge.token(), null,
                HumanTrace.of(12, 1, 0, 320, 0.82, "mouse")).withDuration(1500L));

        assertFalse(result.success());
        assertTrue(result.message().contains("缺少工作量证明"));
    }

    @Test
    void shouldFailWhenSubmittedTooFast() throws Exception {
        CaptchaConfig config = new CaptchaConfig();
        config.setAllowTypes(List.of("human"));
        config.getHuman().setDifficulty(DIFFICULTY);
        config.getHuman().setDelayMinMs(600);
        config.getHuman().setDelayMaxMs(600);
        CaptchaEngine timed = new CaptchaEngine(config, new MemoryCaptchaStore());

        CaptchaChallenge challenge = timed.generate("human", null, null);
        long nonce = ProofOfWork.solve(challenge.token(), challenge.human().salt(), DIFFICULTY, 1 << 20);
        assertNotNull(nonce);

        // 客户端谎报 5 秒也没用 —— 服务端自己计时，实际远不到 600ms
        VerifyResult result = timed.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce,
                HumanTrace.of(12, 1, 0, 320, 0.82, "mouse")).withDuration(5000L));

        assertFalse(result.success());
        assertEquals("操作过快，请重试", result.message());
    }

    @Test
    void shouldPassAfterServerSideDelayElapsed() throws Exception {
        CaptchaConfig config = new CaptchaConfig();
        config.setAllowTypes(List.of("human"));
        config.getHuman().setDifficulty(DIFFICULTY);
        config.getHuman().setDelayMinMs(400);
        config.getHuman().setDelayMaxMs(400);
        CaptchaEngine timed = new CaptchaEngine(config, new MemoryCaptchaStore());

        CaptchaChallenge challenge = timed.generate("human", null, null);
        long nonce = ProofOfWork.solve(challenge.token(), challenge.human().salt(), DIFFICULTY, 1 << 20);
        assertNotNull(nonce);
        Thread.sleep(450); // 真实等过服务端要求的时长

        VerifyResult result = timed.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce,
                HumanTrace.of(12, 1, 0, 320, 0.82, "mouse")).withDuration(450L));

        assertTrue(result.success(), result.message());
    }

    @Test
    void requiredDelayShouldBeRandomizedInRange() {
        CaptchaConfig config = new CaptchaConfig();
        config.setAllowTypes(List.of("human"));
        config.getHuman().setDifficulty(DIFFICULTY);
        config.getHuman().setDelayMinMs(100);
        config.getHuman().setDelayMaxMs(5000);
        CaptchaEngine rnd = new CaptchaEngine(config, new MemoryCaptchaStore());

        long seenMin = Long.MAX_VALUE;
        long seenMax = Long.MIN_VALUE;
        for (int i = 0; i < 30; i++) {
            CaptchaChallenge challenge = rnd.generate("human", null, null);
            long delay = challenge.human().minDurationMs();
            assertTrue(delay >= 100 && delay <= 5000, "随机等待时长越界: " + delay);
            seenMin = Math.min(seenMin, delay);
            seenMax = Math.max(seenMax, delay);
        }
        // 30 次抽样应该覆盖到区间两端附近，证明确实是随机的而不是固定值
        assertTrue(seenMax - seenMin > 1000,
                "30 次抽样的极差应明显大于 0，实际 [" + seenMin + ", " + seenMax + "]");
    }

    @Test
    void shouldFailWhenDurationMissing() {
        CaptchaConfig config = new CaptchaConfig();
        config.setAllowTypes(List.of("human"));
        config.getHuman().setDifficulty(DIFFICULTY);
        config.getHuman().setDelayMinMs(500);
        config.getHuman().setDelayMaxMs(500);
        CaptchaEngine timed = new CaptchaEngine(config, new MemoryCaptchaStore());

        CaptchaChallenge challenge = timed.generate("human", null, null);
        long nonce = ProofOfWork.solve(challenge.token(), challenge.human().salt(), DIFFICULTY, 1 << 20);
        assertNotNull(nonce);
        try {
            Thread.sleep(550);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 少传耗时不能变成绕过计时检查的后门
        VerifyResult result = timed.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce,
                HumanTrace.of(12, 1, 0, 320, 0.82, "mouse")));

        assertFalse(result.success());
        assertEquals("缺少作答耗时", result.message());
    }

    @Test
    void shouldFailWithoutAnyInteraction() {
        CaptchaChallenge challenge = engine.generate("human", null, null);
        long nonce = solve(challenge);

        // 纯 HTTP 请求：没有指针、没有键盘
        VerifyResult result = engine.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce,
                HumanTrace.EMPTY).withDuration(1500L));

        assertFalse(result.success());
        assertTrue(result.message().contains("未检测到"));
    }

    @Test
    void shouldFailWhenTraceMissingButRequired() {
        CaptchaChallenge challenge = engine.generate("human", null, null);
        long nonce = solve(challenge);

        VerifyResult result = engine.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce, null)
                .withDuration(1500L));

        assertFalse(result.success());
    }

    @Test
    void traceCheckCanBeDisabled() {
        CaptchaConfig config = new CaptchaConfig();
        config.setAllowTypes(List.of("human"));
        config.getHuman().setDifficulty(DIFFICULTY);
        config.getHuman().setRequireTrace(false);
        config.getHuman().setDelayMinMs(0);
        config.getHuman().setDelayMaxMs(0);
        CaptchaEngine lenient = new CaptchaEngine(config, new MemoryCaptchaStore());

        CaptchaChallenge challenge = lenient.generate("human", null, null);
        long nonce = ProofOfWork.solve(challenge.token(), challenge.human().salt(), DIFFICULTY, 1 << 20);

        VerifyResult result = lenient.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce, null));
        assertTrue(result.success());
    }

    @Test
    void nonceShouldNotBeReusableAcrossChallenges() {
        CaptchaChallenge first = engine.generate("human", null, null);
        long nonce = solve(first);

        // 换一张验证码：token 与 salt 都变了，同一个 nonce 必然失效
        CaptchaChallenge second = engine.generate("human", null, null);
        VerifyResult result = engine.verify(VerifyInput.human(second.id(), second.token(), nonce,
                HumanTrace.of(12, 1, 0, 320, 0.82, "mouse")).withDuration(1500L));

        assertFalse(result.success());
        assertTrue(result.message().contains("工作量证明无效"));
    }

    @Test
    void passedSessionShouldBeDestroyed() {
        CaptchaChallenge challenge = engine.generate("human", null, null);
        long nonce = solve(challenge);
        HumanTrace trace = HumanTrace.of(12, 1, 0, 320, 0.82, "mouse");

        assertTrue(engine.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce, trace)
                .withDuration(1500L)).success());
        // 一次性：同一个 nonce 再提交一次，会话已经不存在
        assertThrows(CaptchaException.class,
                () -> engine.verify(VerifyInput.human(challenge.id(), challenge.token(), nonce, trace)
                        .withDuration(1500L)));
    }

    @Test
    void shouldLockAfterMaxAttempts() {
        CaptchaChallenge challenge = engine.generate("human", null, null);
        HumanTrace trace = HumanTrace.of(9, 1, 0, 200, 0.9, "mouse");

        for (int i = 1; i <= 3; i++) {
            VerifyResult result = engine.verify(VerifyInput.human(challenge.id(), challenge.token(), -1L, trace)
                    .withDuration(1500L));
            assertFalse(result.success());
            assertEquals(3 - i, result.attemptsLeft());
        }
        assertThrows(CaptchaException.class,
                () -> engine.verify(VerifyInput.human(challenge.id(), challenge.token(), -1L, trace)
                        .withDuration(1500L)));
    }

    @Test
    void powDifficultyShouldBeClamped() {
        assertEquals(0, ProofOfWork.clampDifficulty(0));
        assertEquals(ProofOfWork.MIN_DIFFICULTY, ProofOfWork.clampDifficulty(2));
        assertEquals(ProofOfWork.MAX_DIFFICULTY, ProofOfWork.clampDifficulty(99));
    }

    @Test
    void powSatisfiesShouldRespectLeadingZeroBits() {
        // 手工构造摘要：两位全 0 字节 + 高位 0
        byte[] digest = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0xFF};
        assertTrue(ProofOfWork.satisfies(digest, 8));
        assertTrue(ProofOfWork.satisfies(digest, 16));
        assertTrue(ProofOfWork.satisfies(digest, 20)); // 0x0F = 0000 1111，再借 4 位
        assertFalse(ProofOfWork.satisfies(digest, 21));
        assertFalse(ProofOfWork.satisfies(new byte[]{(byte) 0x80, 0, 0, 0}, 1));
    }

    @Test
    void powDigestShouldMatchJdkSha256() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] expected = md.digest("SC1|token-abc|aabb|42".getBytes(StandardCharsets.UTF_8));

        assertEquals(java.util.HexFormat.of().formatHex(expected), ProofOfWork.digestHex("token-abc", "aabb", 42));
        assertEquals("SC1|token-abc|aabb|42", ProofOfWork.prefix("token-abc", "aabb") + 42);
    }

    @Test
    void solveShouldFindValidNonce() {
        Long nonce = ProofOfWork.solve("tok", "salt", 8, 1 << 16);
        assertNotNull(nonce);
        assertTrue(ProofOfWork.verify("tok", "salt", nonce, 8));
    }

    private long solve(CaptchaChallenge challenge) {
        Long nonce = ProofOfWork.solve(challenge.token(), challenge.human().salt(),
                challenge.human().difficulty(), 1 << 20);
        assertNotNull(nonce, "在 200 万次内没有找到解，难度或哈希实现有问题");
        return nonce;
    }
}
