package cn.localtool.smartcaptcha.core.generator;

import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaConfig;
import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.core.support.ImageSupport;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 点选验证码自定义字体：Font 对象（代码）> 字体文件（路径 / classpath）> 字体族名 > 默认挑选。
 * 不做任何字体校验：设置了外部字体就直接使用；仅字体文件加载失败时回退默认。
 */
class ClickFontTests {

    /** 各平台常见 ttf 字体，找到哪个用哪个；都没有就跳过文件相关用例。 */
    private static final String[] SYSTEM_TTF_CANDIDATES = {
            "/System/Library/Fonts/Supplemental/Arial.ttf",
            "/System/Library/Fonts/Supplemental/Comic Sans.ttf",
            "/System/Library/Fonts/Supplemental/Times New Roman.ttf",
            "/System/Library/Fonts/Supplemental/Verdana.ttf",
            "/Library/Fonts/Arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
            "C:/Windows/Fonts/arial.ttf",
    };

    private final ClickWordCaptchaGenerator generator = new ClickWordCaptchaGenerator();
    private final CaptchaConfig config = new CaptchaConfig();

    @Test
    void fontObjectWinsOverEverything() {
        config.getClick().setFont(new Font(Font.MONOSPACED, Font.ITALIC, 99));
        config.getClick().setFontFamily("Serif");

        Font resolved = generator.resolveFont(config.getClick(), 40, false);

        assertEquals(Font.MONOSPACED, resolved.getFamily());
        assertEquals(Font.ITALIC, resolved.getStyle());
        assertEquals(40, resolved.getSize());
    }

    @Test
    void fontObjectKeepsStyleButDerivesSize() {
        config.getClick().setFont(new Font(Font.SERIF, Font.PLAIN, 12));
        config.getClick().setFontSize(30);

        Font resolved = generator.resolveFont(config.getClick(), config.getClick().getFontSize(), false);

        assertEquals(30, resolved.getSize());
        assertEquals(Font.PLAIN, resolved.getStyle());
    }

    @Test
    void fontFamilyIsUsedWhenAvailable() {
        config.getClick().setFontFamily("Monospaced");

        Font resolved = generator.resolveFont(config.getClick(), 36, false);

        assertEquals("Monospaced", resolved.getFamily());
        assertEquals(36, resolved.getSize());
    }

    @Test
    void fontObjectIsUsedWithoutAnyValidation() {
        // 即使是 zh 字符集也不校验能否渲染汉字：设置了就直接用
        config.getClick().setFont(new Font(Font.DIALOG, Font.PLAIN, 11));

        Font resolved = generator.resolveFont(config.getClick(), 30, true);

        assertEquals(Font.DIALOG, resolved.getFamily());
        assertEquals(30, resolved.getSize());
    }

    @Test
    void fontFamilyIsPassedThroughAsIs() {
        // 未安装的字体族名不做校验、不回退，直接交给 JVM 映射
        config.getClick().setFontFamily("NoSuchFont-You-Know-It");

        Font resolved = generator.resolveFont(config.getClick(), 30, false);

        assertNotNull(resolved);
        assertEquals(30, resolved.getSize());
    }

    @Test
    void blankFamilyUsesDefaultSelection() {
        assertEquals(Font.SANS_SERIF, generator.resolveFont(config.getClick(), 30, false).getFamily());
    }

    @Test
    void generateWithCustomFontProducesWords() {
        config.getClick().setCharset("en");
        config.getClick().setFontSize(28);
        config.getClick().setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));

        CaptchaChallenge challenge = new CaptchaEngine(config).generate("click");

        assertEquals("click", challenge.type());
        assertEquals(config.getClick().getWordCount(), challenge.click().words().size());
    }

    // ------------------------------------------------------------ 字体文件（路径 / classpath）

    @Test
    void fontObjectWinsOverFontPath() throws Exception {
        Path ttf = findSystemTtf();
        assumeTrue(ttf != null, "环境里找不到 ttf 字体文件，跳过");

        config.getClick().setFontPath(ttf.toString());
        config.getClick().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));

        assertEquals(Font.MONOSPACED, generator.resolveFont(config.getClick(), 30, false).getFamily());
    }

    @Test
    void fontFileIsLoadedFromAbsolutePath() throws Exception {
        Path ttf = findSystemTtf();
        assumeTrue(ttf != null, "环境里找不到 ttf 字体文件，跳过");

        config.getClick().setFontPath(ttf.toString());
        Font resolved = generator.resolveFont(config.getClick(), 32, false);

        assertEquals(ImageSupport.loadFont(ttf.toString()).getFamily(), resolved.getFamily());
        assertEquals(32, resolved.getSize());
        assertEquals(Font.BOLD, resolved.getStyle());
    }

    @Test
    void fontFileIsLoadedFromClasspath() throws Exception {
        Path ttf = findSystemTtf();
        assumeTrue(ttf != null, "环境里找不到 ttf 字体文件，跳过");

        // 运行期把字体拷进 target/test-classes，模拟打包在 jar/classpath 里的字体资源
        Path dir = Path.of("target", "test-classes", "fonts");
        Files.createDirectories(dir);
        Path resource = dir.resolve("runtime-test.ttf");
        if (Files.notExists(resource)) {
            Files.copy(ttf, resource);
        }

        config.getClick().setFontPath("classpath:fonts/runtime-test.ttf");
        Font resolved = generator.resolveFont(config.getClick(), 30, false);

        assertNotNull(resolved);
        assertNotEquals(Font.SANS_SERIF, resolved.getFamily());
        assertEquals(30, resolved.getSize());
    }

    @Test
    void sameFontFileIsCachedAcrossCalls() throws Exception {
        Path ttf = findSystemTtf();
        assumeTrue(ttf != null, "环境里找不到 ttf 字体文件，跳过");

        assertSame(ImageSupport.loadFont(ttf.toString()), ImageSupport.loadFont(ttf.toString()));
    }

    @Test
    void missingFontFileFallsBackToDefault() {
        config.getClick().setFontPath("/no/such/dir/font.ttf");
        assertEquals(Font.SANS_SERIF, generator.resolveFont(config.getClick(), 30, false).getFamily());
    }

    @Test
    void unknownClasspathFontFallsBackToDefault() {
        config.getClick().setFontPath("classpath:fonts/no-such-font.ttf");
        assertEquals(Font.SANS_SERIF, generator.resolveFont(config.getClick(), 30, false).getFamily());
    }

    @Test
    void corruptFontFileFallsBackToDefault() throws Exception {
        Path garbage = Files.createTempFile("captcha-broken", ".ttf");
        Files.write(garbage, "this is not a font".getBytes(StandardCharsets.US_ASCII));

        config.getClick().setFontPath(garbage.toString());
        assertEquals(Font.SANS_SERIF, generator.resolveFont(config.getClick(), 30, false).getFamily());
    }

    /** 找一个系统里真实存在的 ttf 文件，找不到返回 null。 */
    private Path findSystemTtf() {
        for (String candidate : SYSTEM_TTF_CANDIDATES) {
            Path p = Path.of(candidate);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }
}
