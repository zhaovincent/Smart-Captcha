package cn.localtool.smartcaptcha.core.generator;

import cn.localtool.smartcaptcha.core.support.ImageSupport;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 回归：绘制文字必须真正使用指定字体。
 *
 * <p>历史缺陷：drawWord 用 {@code g.getFont()}（Graphics2D 默认字体）而非解析出的
 * 外部字体，导致 {@code font-path} / {@code setFont} 只影响排版测量、不影响画面。</p>
 */
class ClickFontDrawTests {

    private static final ClickWordCaptchaGenerator.Placement PLACEMENT =
            new ClickWordCaptchaGenerator.Placement("啊", 60, 50, 0);

    @Test
    void drawnGlyphsDependOnTheGivenFont() {
        BufferedImage withSerif = draw(new Font(Font.SERIF, Font.BOLD, 40));
        BufferedImage withMono = draw(new Font(Font.MONOSPACED, Font.BOLD, 40));

        // 不同字体必须画出不同的字形（修复前两者都走默认字体，完全相同）
        assertNotEquals(0, diffPixels(withSerif, withMono), "换字体后画面应有差异");
    }

    @Test
    void drawingIsDeterministicForTheSameFont() {
        BufferedImage a = draw(new Font(Font.SERIF, Font.BOLD, 40));
        BufferedImage b = draw(new Font(Font.SERIF, Font.BOLD, 40));

        assertEquals(0, diffPixels(a, b));
    }

    // ------------------------------------------------------------------ 工具

    private BufferedImage draw(Font font) {
        BufferedImage image = new BufferedImage(120, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        ImageSupport.applyQualityHints(g);
        new ClickWordCaptchaGenerator().drawWord(g, font, PLACEMENT, new Random(42));
        g.dispose();
        return image;
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
