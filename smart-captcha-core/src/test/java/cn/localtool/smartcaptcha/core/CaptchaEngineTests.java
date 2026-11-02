package cn.localtool.smartcaptcha.core;

import cn.localtool.smartcaptcha.core.generator.CaptchaGenerator;
import cn.localtool.smartcaptcha.core.store.MemoryCaptchaStore;
import cn.localtool.smartcaptcha.core.support.ImageSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内核行为测试：三种验证方式的通过与失败、次数锁定、字符集、外部背景图、
 * 自定义生成器与配置开关。完全不依赖 Spring。
 */
class CaptchaEngineTests {

    private MemoryCaptchaStore store;
    private CaptchaEngine engine;

    @BeforeEach
    void setUp() {
        store = new MemoryCaptchaStore();
        engine = new CaptchaEngine(new CaptchaConfig(), store);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void sliderShouldPassWithExactTarget() {
        CaptchaChallenge challenge = engine.generate("slider", null, null);
        Double targetX = store.find(challenge.id()).orElseThrow().getAnswer().targetX();

        VerifyResult result = engine.verify(VerifyInput.slider(challenge.id(), challenge.token(), targetX));

        assertTrue(result.success());
        assertNotNull(result.ticket());
        // ticket 一次性
        assertTrue(store.consumeTicket(result.ticket()));
        assertFalse(store.consumeTicket(result.ticket()));
    }

    @Test
    void sliderShouldFailAndLockAfterMaxAttempts() {
        CaptchaChallenge challenge = engine.generate("slider", null, null);
        String id = challenge.id();

        for (int i = 1; i <= 3; i++) {
            VerifyResult result = engine.verify(VerifyInput.slider(id, challenge.token(), -999d));
            assertFalse(result.success());
            assertEquals(3 - i, result.attemptsLeft());
        }

        // 次数耗尽后会话已销毁
        assertThrows(CaptchaException.class,
                () -> engine.verify(VerifyInput.slider(id, challenge.token(), -999d)));
    }

    @Test
    void clickShouldPassInPromptOrder() {
        CaptchaChallenge challenge = engine.generate("click", null, null);
        List<double[]> points = store.find(challenge.id()).orElseThrow().getAnswer().points();

        List<Coordinate> clicks = new ArrayList<>();
        for (double[] p : points) {
            clicks.add(new Coordinate(p[0] + 3, p[1] - 2));
        }
        assertTrue(engine.verify(VerifyInput.click(challenge.id(), challenge.token(), clicks)).success());
    }

    @Test
    void clickShouldFailWithWrongOrder() {
        CaptchaChallenge challenge = engine.generate("click", null, null);
        List<double[]> points = store.find(challenge.id()).orElseThrow().getAnswer().points();

        List<Coordinate> reversed = new ArrayList<>();
        for (int i = points.size() - 1; i >= 0; i--) {
            reversed.add(new Coordinate(points.get(i)[0], points.get(i)[1]));
        }
        assertFalse(engine.verify(VerifyInput.click(challenge.id(), challenge.token(), reversed)).success());
    }

    @Test
    void rotateShouldPassWithTargetAngle() {
        CaptchaChallenge challenge = engine.generate("rotate", null, null);
        double targetAngle = store.find(challenge.id()).orElseThrow().getAnswer().targetAngle();

        assertTrue(engine.verify(VerifyInput.rotate(challenge.id(), challenge.token(), targetAngle)).success());
    }

    @Test
    void rotateShouldTolerateNegativeAndWrappedAngle() {
        CaptchaChallenge challenge = engine.generate("rotate", null, null);
        double target = store.find(challenge.id()).orElseThrow().getAnswer().targetAngle();
        double wrapped = target > 0 ? target - 360 : target + 360;

        assertTrue(engine.verify(VerifyInput.rotate(challenge.id(), challenge.token(), wrapped)).success());
    }

    @Test
    void clickShouldUseChineseByDefault() {
        CaptchaChallenge challenge = engine.generate("click", null, null);
        // 默认中文；极少数无中文字体的环境会自动降级为英文
        String expected = ImageSupport.supportsCjk() ? "zh" : "en";
        assertEquals(expected, challenge.click().charset());
        if ("zh".equals(expected)) {
            assertTrue(challenge.click().words().stream().allMatch(w -> w.codePointAt(0) >= 0x4E00));
            assertTrue(challenge.tip().startsWith("请依次点击"));
        }
    }

    @Test
    void clickShouldSupportEnglishCharset() {
        CaptchaChallenge challenge = engine.generate("click", null, "en");
        assertEquals("en", challenge.click().charset());
        assertEquals(3, challenge.click().words().size());
        assertTrue(challenge.click().words().stream().allMatch(w -> w.matches("[A-Z]")));
        assertTrue(challenge.tip().startsWith("Click in order"));

        // 英文字符点选同样能按提示顺序校验通过
        List<Coordinate> clicks = new ArrayList<>();
        for (double[] p : store.find(challenge.id()).orElseThrow().getAnswer().points()) {
            clicks.add(new Coordinate(p[0], p[1]));
        }
        assertTrue(engine.verify(VerifyInput.click(challenge.id(), challenge.token(), clicks)).success());
    }

    @Test
    void clickCharsetShouldFallBackToDefaultWhenUnknown() {
        CaptchaChallenge challenge = engine.generate("click", null, "klingon");
        assertEquals(ImageSupport.supportsCjk() ? "zh" : "en", challenge.click().charset());
    }

    @Test
    void externalBackgroundShouldBeAccepted() {
        BufferedImage image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 90, 160));
        g.fillRect(0, 0, 640, 360);
        g.setColor(new Color(240, 200, 60));
        g.fillOval(120, 60, 220, 220);
        g.dispose();

        CaptchaChallenge challenge = engine.generate("slider", ImageSupport.toDataUrl(image), null);
        assertEquals(360, challenge.width());
        assertEquals(200, challenge.height());

        double targetX = store.find(challenge.id()).orElseThrow().getAnswer().targetX();
        assertTrue(engine.verify(VerifyInput.slider(challenge.id(), challenge.token(), targetX)).success());
    }

    @Test
    void invalidTokenShouldBeRejected() {
        CaptchaChallenge challenge = engine.generate("slider", null, null);
        assertThrows(CaptchaException.class,
                () -> engine.verify(VerifyInput.slider(challenge.id(), "bad-token", 100d)));
    }

    @Test
    void autoTypeShouldStayInsideAllowedPool() {
        CaptchaConfig config = new CaptchaConfig();
        config.setAllowTypes(List.of("click"));
        CaptchaEngine clickOnly = new CaptchaEngine(config, store);

        for (int i = 0; i < 5; i++) {
            assertEquals("click", clickOnly.generate("auto", null, "en").type());
        }
        assertThrows(CaptchaException.class, () -> clickOnly.generate("slider", null, null));
    }

    @Test
    void disabledEngineShouldRejectGenerate() {
        CaptchaConfig config = new CaptchaConfig();
        config.setEnabled(false);
        CaptchaEngine disabled = new CaptchaEngine(config, store);
        assertThrows(CaptchaException.class, disabled::generate);
    }

    @Test
    void minDurationShouldRejectInstantSubmission() {
        CaptchaConfig config = new CaptchaConfig();
        config.setMinDurationMs(800);
        CaptchaEngine strict = new CaptchaEngine(config, store);

        CaptchaChallenge challenge = strict.generate("slider");
        double targetX = store.find(challenge.id()).orElseThrow().getAnswer().targetX();

        assertFalse(strict.verify(VerifyInput.slider(challenge.id(), challenge.token(), targetX)
                .withDuration(120L)).success());
        assertTrue(strict.verify(VerifyInput.slider(challenge.id(), challenge.token(), targetX)
                .withDuration(1500L)).success());
    }

    @Test
    void customGeneratorShouldOverrideBuiltIn() {
        CaptchaGenerator custom = new CaptchaGenerator() {
            @Override
            public CaptchaType type() {
                return CaptchaType.SLIDER;
            }

            @Override
            public GenerationResult generate(BufferedImage background, CaptchaConfig config, Random random) {
                return GenerationResult.of(background, CaptchaAnswer.slider(42, 10),
                        new CaptchaChallenge.SliderChallenge("data:image/png;base64,AA==", 10, 10, 5),
                        "自定义滑块");
            }
        };
        CaptchaEngine custom1 = CaptchaEngine.builder(new CaptchaConfig()).store(store)
                .generator(custom).build();

        CaptchaChallenge challenge = custom1.generate("slider");
        assertEquals("自定义滑块", challenge.tip());
        assertTrue(custom1.verify(VerifyInput.slider(challenge.id(), challenge.token(), 42)).success());
    }

    @Test
    void publicConfigShouldExposeNoAnswers() {
        var publicConfig = engine.publicConfig();
        assertEquals(List.of("slider", "click", "rotate"), publicConfig.get("types"));
        assertEquals("memory", publicConfig.get("store"));
        assertFalse(publicConfig.containsKey("answer"));
    }
}
