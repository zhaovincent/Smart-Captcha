package cn.localtool.smartcaptcha.core.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片与字体相关工具方法（纯 JDK）。
 */
public final class ImageSupport {

    private static final String DATA_URL_PREFIX = "data:image/";

    private ImageSupport() {
    }

    public static void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /** PNG -> dataURL，前端 <img> 可直接使用。 */
    public static String toDataUrl(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage rgb = image;
            if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
                rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = rgb.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();
            }
            ImageIO.write(rgb, "png", out);
            return DATA_URL_PREFIX + "png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("验证码图片编码失败", e);
        }
    }

    /** 解析 dataURL / 纯 base64 为图片，失败返回 null。 */
    public static BufferedImage readBase64(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String base64 = raw;
        int comma = raw.indexOf(',');
        if (raw.startsWith("data:") && comma > 0) {
            base64 = raw.substring(comma + 1);
        }
        base64 = base64.replaceAll("\\s", "");
        if (base64.isEmpty() || base64.length() > 12 * 1024 * 1024) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IllegalArgumentException | IOException e) {
            return null;
        }
    }

    /** 等比缩放后居中裁剪到目标尺寸（cover 语义）。 */
    public static BufferedImage cover(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        applyQualityHints(g);
        double scale = Math.max(targetWidth / (double) source.getWidth(), targetHeight / (double) source.getHeight());
        int drawWidth = (int) Math.ceil(source.getWidth() * scale);
        int drawHeight = (int) Math.ceil(source.getHeight() * scale);
        int dx = (targetWidth - drawWidth) / 2;
        int dy = (targetHeight - drawHeight) / 2;
        g.drawImage(source, dx, dy, drawWidth, drawHeight, null);
        g.dispose();
        return canvas;
    }

    public static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        applyQualityHints(g);
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static final Map<String, Font> FONT_FILE_CACHE = new ConcurrentHashMap<>();

    /**
     * 从文件系统路径或 {@code classpath:} 资源加载字体文件（ttf / otf），
     * 返回字号为 1 的基础字体（调用方按需 {@code deriveFont}）。
     *
     * <p>支持 {@code ~} 展开；文件按「绝对路径 + 最后修改时间」缓存，classpath 资源
     * 按坐标缓存——同一字体只读一次。加载失败（文件不存在 / 格式不对 / ttc 集合）
     * 返回 {@code null}，由调用方回退默认字体。</p>
     */
    public static Font loadFont(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String raw = location.trim();
        String cacheKey;
        File file = null;
        URL url = null;
        if (raw.startsWith("classpath:")) {
            String resource = raw.substring("classpath:".length()).trim();
            if (resource.isEmpty()) {
                return null;
            }
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {
                loader = ImageSupport.class.getClassLoader();
            }
            url = loader.getResource(resource);
            if (url == null) {
                return null;
            }
            cacheKey = raw;
        } else {
            String path = raw.startsWith("~")
                    ? System.getProperty("user.home") + raw.substring(1)
                    : raw;
            file = new File(path);
            if (!file.isFile()) {
                return null;
            }
            cacheKey = file.getAbsolutePath() + "|" + file.lastModified();
        }

        Font cached = FONT_FILE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try (InputStream in = url != null ? url.openStream() : new BufferedInputStream(new FileInputStream(file))) {
            Font base = Font.createFont(Font.TRUETYPE_FONT, in);
            FONT_FILE_CACHE.put(cacheKey, base);
            return base;
        } catch (FontFormatException | IOException e) {
            return null;
        }
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
