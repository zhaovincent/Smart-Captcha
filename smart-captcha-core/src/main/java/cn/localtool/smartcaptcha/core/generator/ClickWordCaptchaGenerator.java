package cn.localtool.smartcaptcha.core.generator;

import cn.localtool.smartcaptcha.core.CaptchaAnswer;
import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaConfig;
import cn.localtool.smartcaptcha.core.CaptchaType;
import cn.localtool.smartcaptcha.core.GenerationResult;
import cn.localtool.smartcaptcha.core.support.ImageSupport;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 文字点选验证码：在背景上随机绘制若干字符，用户按提示顺序点击对应文字。
 *
 * <p>字符集由 {@code click.charset} 控制（{@code zh} 中文、默认；{@code en} 英文），
 * 也可在单次生成请求中通过 {@code clickCharset} 覆盖。</p>
 *
 * <p>字体可自定义：代码里调 {@code config.getClick().setFont(Font)} 传入字体对象，
 * 或配置 {@code smart-captcha.click.font-path} 指定字体文件（本地路径 /
 * {@code classpath:} 资源）、{@code smart-captcha.click.font-family} 指定字体族名；
 * 未设置时自动挑选可用的中文字体（英文用 SansSerif）。</p>
 */
public class ClickWordCaptchaGenerator implements CaptchaGenerator {

    private static final Logger log = Logger.getLogger(ClickWordCaptchaGenerator.class.getName());

    public static final String CHARSET_ZH = "zh";
    public static final String CHARSET_EN = "en";

    /** 中文字符池（避开容易混淆的字形）。 */
    private static final String[] CJK_POOL = {
            "验", "证", "码", "点", "击", "滑", "块", "角", "度", "旋", "转", "安", "全", "防", "护",
            "智", "能", "图", "形", "识", "别", "人", "机", "交", "互", "数", "据", "网", "络", "云"
    };

    /** 英文字符池（大写字母，去掉 I/O/Q 等易与数字混淆的字符）。 */
    private static final String[] LATIN_POOL = {
            "A", "B", "C", "D", "E", "F", "G", "H", "K", "M", "N", "P", "R", "S", "T", "U", "X", "Y", "Z"
    };

    private static final Color[] TEXT_COLORS = {
            new Color(0x0F172A), new Color(0x0F766E), new Color(0x7C3AED),
            new Color(0xB91C1C), new Color(0xA16207), new Color(0x1D4ED8)
    };

    @Override
    public CaptchaType type() {
        return CaptchaType.CLICK;
    }

    @Override
    public GenerationResult generate(BufferedImage background, CaptchaConfig config, Random random) {
        return generate(background, config, random, GenerateOptions.DEFAULT);
    }

    @Override
    public GenerationResult generate(BufferedImage background, CaptchaConfig config,
                                     Random random, GenerateOptions options) {
        int width = background.getWidth();
        int height = background.getHeight();
        int fontSize = Math.min(config.getClick().getFontSize(), Math.max(18, height / 5));
        int count = Math.max(2, Math.min(config.getClick().getWordCount(), 5));

        String charset = resolveCharset(options == null ? null : options.clickCharset(),
                config.getClick().getCharset());
        boolean cjk = CHARSET_ZH.equals(charset);
        String[] pool = cjk ? CJK_POOL : LATIN_POOL;
        List<String> picked = pickWords(pool, count, random);

        Font font = resolveFont(config.getClick(), fontSize, cjk);

        BufferedImage canvas = ImageSupport.copy(background);
        Graphics2D g = canvas.createGraphics();
        ImageSupport.applyQualityHints(g);
        FontRenderContext frc = g.getFontRenderContext();

        List<Placement> placements = new ArrayList<>();
        for (String word : picked) {
            Placement placement = placeWord(word, font, frc, width, height, placements, random);
            if (placement == null) {
                continue;
            }
            placements.add(placement);
            drawWord(g, placement, random);
        }

        // 打乱顺序即“需要依次点击的顺序”，答案坐标同步打乱
        List<Placement> ordered = new ArrayList<>(placements);
        Collections.shuffle(ordered, random);

        List<String> words = new ArrayList<>();
        List<double[]> points = new ArrayList<>();
        for (Placement p : ordered) {
            words.add(p.word());
            points.add(new double[]{p.centerX(), p.centerY()});
        }
        g.dispose();

        String tip = cjk
                ? "请依次点击：" + String.join(" → ", words)
                : "Click in order: " + String.join(" → ", words);
        return GenerationResult.of(canvas, CaptchaAnswer.click(points),
                new CaptchaChallenge.ClickChallenge(words, charset), tip);
    }

    /**
     * 决定本次绘制使用的字体，优先级：
     * <ol>
     *   <li>{@code click.font} —— 代码里传入的 {@link Font} 对象（最高）；</li>
     *   <li>{@code click.fontPath} —— 字体文件（绝对/相对路径或 {@code classpath:} 资源，ttf/otf）；</li>
     *   <li>{@code click.fontFamily} —— 字体族名（可由 yaml 配置）；</li>
     *   <li>默认：中文选可用的中文字体，英文用 SansSerif。</li>
     * </ol>
     * 自定义字体的字号按参数派生（来自 {@code click.font-size}），Font 对象保留传入样式，
     * 字体文件按加粗绘制；文件加载失败、字体族未安装、或中文场景渲染不了汉字时，
     * 回退到默认选择并打 warn。
     */
    Font resolveFont(CaptchaConfig.Click click, int size, boolean cjk) {
        Font custom = click.getFont();
        if (custom != null) {
            if (cjk && !ImageSupport.canDisplayCjk(custom)) {
                log.warning("自定义 Font 无法渲染中文，点选验证码回退到默认中文字体");
                return ImageSupport.cjkFont(size, Font.BOLD);
            }
            return custom.deriveFont((float) size);
        }

        String path = click.getFontPath();
        if (path != null && !path.isBlank()) {
            Font base = ImageSupport.loadFont(path);
            if (base == null) {
                log.warning("自定义字体文件「" + path + "」加载失败，点选验证码回退到默认字体");
            } else {
                Font resolved = base.deriveFont(Font.BOLD, (float) size);
                if (cjk && !ImageSupport.canDisplayCjk(resolved)) {
                    log.warning("自定义字体文件「" + path + "」不支持中文，点选验证码回退到默认中文字体");
                } else {
                    return resolved;
                }
            }
        }

        String family = click.getFontFamily();
        if (family != null && !family.isBlank()) {
            if (!ImageSupport.familyAvailable(family)) {
                log.warning("自定义字体族「" + family + "」未安装，点选验证码回退到默认字体");
            } else {
                Font resolved = new Font(family.trim(), Font.BOLD, size);
                if (cjk && !ImageSupport.canDisplayCjk(resolved)) {
                    log.warning("自定义字体族「" + family + "」不支持中文，点选验证码回退到默认中文字体");
                } else {
                    return resolved;
                }
            }
        }
        return cjk ? ImageSupport.cjkFont(size, Font.BOLD) : new Font(Font.SANS_SERIF, Font.BOLD, size);
    }

    /**
     * 决定本次使用的字符集：请求参数优先，其次全局配置；环境无中文字体时自动降级为英文。
     */
    private String resolveCharset(String override, String configured) {
        String raw = override != null && !override.isBlank() ? override : configured;
        String normalized = normalizeCharset(raw);
        if (CHARSET_ZH.equals(normalized)) {
            if (ImageSupport.supportsCjk()) {
                return CHARSET_ZH;
            }
            log.warning("当前环境缺少中文字体，点选验证码自动降级为英文字符");
            return CHARSET_EN;
        }
        if (CHARSET_EN.equals(normalized)) {
            return CHARSET_EN;
        }
        log.warning("未知的点选字符集配置「" + raw + "」，按默认值 " + CHARSET_ZH + " 处理");
        return ImageSupport.supportsCjk() ? CHARSET_ZH : CHARSET_EN;
    }

    private String normalizeCharset(String raw) {
        if (raw == null) {
            return CHARSET_ZH;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("zh") || value.equals("cn") || value.equals("chinese")
                || value.equals("han") || value.equals("zh_cn")) {
            return CHARSET_ZH;
        }
        if (value.startsWith("en") || value.equals("latin") || value.equals("english")) {
            return CHARSET_EN;
        }
        return value;
    }

    private List<String> pickWords(String[] pool, int count, Random random) {
        List<String> all = new ArrayList<>(Arrays.asList(pool));
        Collections.shuffle(all, random);
        Set<String> picked = new LinkedHashSet<>(all.subList(0, Math.min(count, all.size())));
        return new ArrayList<>(picked);
    }

    private Placement placeWord(String word, Font font, FontRenderContext frc,
                                int width, int height, List<Placement> existing, Random random) {
        GlyphVector probe = font.createGlyphVector(frc, word);
        Rectangle2D bounds = probe.getLogicalBounds();
        double textWidth = Math.max(10, bounds.getWidth());
        double textHeight = Math.max(10, font.getSize() * 1.1);

        int pad = 10;
        for (int attempt = 0; attempt < 240; attempt++) {
            double cx = pad + textWidth / 2 + random.nextDouble() * Math.max(1, width - textWidth - pad * 2);
            double cy = pad + textHeight / 2 + random.nextDouble() * Math.max(1, height - textHeight - pad * 2);
            boolean overlap = false;
            for (Placement p : existing) {
                if (Math.hypot(p.centerX() - cx, p.centerY() - cy) < font.getSize() * 1.8) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) {
                continue;
            }
            double rotate = Math.toRadians(random.nextInt(41) - 20);
            return new Placement(word, cx, cy, rotate);
        }
        return null;
    }

    private void drawWord(Graphics2D g, Placement placement, Random random) {
        AffineTransform origin = g.getTransform();
        g.rotate(placement.rotate(), placement.centerX(), placement.centerY());

        GlyphVector glyphVector = g.getFont().createGlyphVector(g.getFontRenderContext(), placement.word());
        Rectangle2D bounds = glyphVector.getLogicalBounds();
        double x = placement.centerX() - bounds.getWidth() / 2;
        double y = placement.centerY() + (bounds.getHeight() * 0.72) / 2;
        Shape outline = glyphVector.getOutline((float) x, (float) y);

        // 白色描边保证在任意背景上可读
        g.setColor(new Color(255, 255, 255, 232));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outline);

        g.setColor(TEXT_COLORS[random.nextInt(TEXT_COLORS.length)]);
        g.fill(outline);

        g.setTransform(origin);
    }

    private record Placement(String word, double centerX, double centerY, double rotate) {
    }
}
