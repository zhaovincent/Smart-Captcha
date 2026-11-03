package cn.localtool.smartcaptcha.core.generator;

import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaConfig;
import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.core.Coordinate;
import cn.localtool.smartcaptcha.core.VerifyInput;
import cn.localtool.smartcaptcha.core.store.MemoryCaptchaStore;
import cn.localtool.smartcaptcha.core.support.ImageSupport;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 点选展示数与点击数分离：{@code click.display-count} 个字画在图上（含干扰项），
 * 只有 {@code click.word-count} 个需要点击、进入提示与答案。
 */
class ClickDistractorTests {

    private final ClickWordCaptchaGenerator generator = new ClickWordCaptchaGenerator();

    private BufferedImage background() {
        BufferedImage bg = new BufferedImage(310, 155, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = bg.createGraphics();
        g.setColor(new java.awt.Color(210, 220, 230));
        g.fillRect(0, 0, 310, 155);
        g.dispose();
        return bg;
    }

    @Test
    void displayCountLargerThanWordCountDrawsDistractors() {
        CaptchaConfig config = new CaptchaConfig();
        config.getClick().setWordCount(3);
        config.getClick().setDisplayCount(6);

        // 相同随机种子下，展示 6 字与展示 3 字的画面应当不同（多画了干扰项）
        BufferedImage withDistractors = generator.generate(background(), config, new Random(7)).image();

        CaptchaConfig plain = new CaptchaConfig();
        plain.getClick().setWordCount(3);
        BufferedImage withoutDistractors = generator.generate(background(), plain, new Random(7)).image();

        assertNotEquals(0, diffPixels(withDistractors, withoutDistractors), "干扰项应画在图上");
    }

    @Test
    void answerAndTipOnlyContainRequiredWords() {
        CaptchaConfig config = new CaptchaConfig();
        config.getClick().setWordCount(3);
        config.getClick().setDisplayCount(6);

        CaptchaChallenge challenge = new CaptchaEngine(config).generate("click");

        // 提示与答案只有需要点击的 3 个字
        assertEquals(3, challenge.click().words().size());
    }

    @Test
    void displayCountSmallerThanWordCountIsClamped() {
        CaptchaConfig config = new CaptchaConfig();
        config.getClick().setWordCount(3);
        config.getClick().setDisplayCount(1);

        CaptchaChallenge challenge = new CaptchaEngine(config).generate("click");

        // 展示数小于点击数时按点击数处理
        assertEquals(3, challenge.click().words().size());
    }

    @Test
    void zeroDisplayCountKeepsBackwardCompatibleBehaviour() {
        CaptchaConfig config = new CaptchaConfig();
        config.getClick().setWordCount(3);

        CaptchaChallenge challenge = new CaptchaEngine(config).generate("click");

        assertEquals(3, challenge.click().words().size());
    }

    @Test
    void requiredWordsStillVerifiableWithDistractors() {
        CaptchaConfig config = new CaptchaConfig();
        config.getClick().setWordCount(3);
        config.getClick().setDisplayCount(6);

        MemoryCaptchaStore store = new MemoryCaptchaStore();
        CaptchaEngine engine = new CaptchaEngine(config, store);
        CaptchaChallenge challenge = engine.generate("click");
        List<double[]> answer = store.find(challenge.id()).orElseThrow().getAnswer().points();

        List<Coordinate> clicks = new ArrayList<>();
        for (double[] p : answer) {
            clicks.add(new Coordinate(p[0], p[1]));
        }
        assertTrue(engine.verify(VerifyInput.click(challenge.id(), challenge.token(), clicks)).success(),
                "按答案坐标点击必点字应通过（干扰项不影响校验）");
        store.close();
    }

    private int diffPixels(BufferedImage a, BufferedImage b) {
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(a.getHeight(), b.getHeight());
        int diff = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    diff++;
                }
            }
        }
        return diff;
    }
}
