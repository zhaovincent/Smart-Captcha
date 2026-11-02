package cn.localtool.smartcaptcha.core.support;

import cn.localtool.smartcaptcha.core.CaptchaConfig;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 背景图工厂（纯 JDK）。
 *
 * <p>默认使用纯代码程序化绘制（渐变 + 光晕 + 山峦 + 光带 + 噪点），无需任何素材即可开箱使用。
 * 也可以配置若干「背景图来源」，配置后按比例随机取用真实图片，来源支持四种写法：</p>
 *
 * <pre>{@code
 * // 1) 目录：递归扫描其中的 png/jpg（最多 5 层）
 * "/data/captcha/bg"
 * "~/Pictures/captcha"
 * // 2) 单个本地文件
 * "/data/captcha/bg/01.jpg"
 * // 3) 远程图片 URL（启动时预取并缓存在内存，可配置过期时间）
 * "https://cdn.example.com/bg/02.jpg"
 * // 4) classpath 资源（打 fat jar 时把图片一起打进去）
 * "classpath:captcha-bg/03.png"
 * "classpath:captcha-bg/*"      // 目录下全部图片；** 表示递归
 * }</pre>
 *
 * <p>任一来源不可用（目录不存在、URL 404、图片损坏）都不会影响验证码生成，
 * 只会退化到程序化背景，并打印一条告警日志。</p>
 */
public class BackgroundFactory {

    private static final Logger log = Logger.getLogger(BackgroundFactory.class.getName());
    private static final String[] IMAGE_EXT = {".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp"};
    /** 目录递归扫描的最大层数，防止误配根目录时扫爆磁盘。 */
    private static final int MAX_SCAN_DEPTH = 5;
    /** 单个目录最多收录的图片数量。 */
    private static final int MAX_IMAGES_PER_DIR = 5000;
    /** 远程图片失败后的重试冷却时间。 */
    private static final long REMOTE_RETRY_DELAY_MS = 15_000L;

    private record Palette(Color top, Color mid, Color bottom, Color glow, Color hill) {
    }

    private static final Palette[] PALETTES = {
            new Palette(new Color(0x1B2A4A), new Color(0x5B6C8F), new Color(0xF7B267), new Color(0xFF8C69), new Color(0x101B33)),
            new Palette(new Color(0x0F766E), new Color(0x2DD4BF), new Color(0xD1FAE5), new Color(0x99F6E4), new Color(0x04302C)),
            new Palette(new Color(0x4C1D95), new Color(0x8B5CF6), new Color(0xFDA4AF), new Color(0xF0ABFC), new Color(0x2A0F55)),
            new Palette(new Color(0x0C4A6E), new Color(0x0284C7), new Color(0x67E8F9), new Color(0x7DD3FC), new Color(0x072F46)),
            new Palette(new Color(0x7C2D12), new Color(0xEA580C), new Color(0xFCD34D), new Color(0xFDE68A), new Color(0x431407)),
            new Palette(new Color(0x14532D), new Color(0x22C55E), new Color(0xD9F99D), new Color(0xBEF264), new Color(0x052E16)),
            new Palette(new Color(0x312E81), new Color(0x6366F1), new Color(0xC7D2FE), new Color(0xA5B4FC), new Color(0x1E1B4B)),
            new Palette(new Color(0x831843), new Color(0xEC4899), new Color(0xFBCFE8), new Color(0xF9A8D4), new Color(0x500724))
    };

    /** 已解析出来的可用图片来源。 */
    private final List<ImageSource> sources = new CopyOnWriteArrayList<>();
    /** 配置里原始的来源列表，供 {@link #reload()} 重新解析。 */
    private final List<String> configured = new CopyOnWriteArrayList<>();
    /** 使用外部图片的百分比（0~100），剩下的走程序化绘制。 */
    private final int ratio;
    /** 远程图片缓存有效期，0 表示只在启动时取一次。 */
    private final long refreshMs;
    private final int readTimeoutMs;
    private final HttpClient http;

    /** 纯程序化背景。 */
    public BackgroundFactory() {
        this(new CaptchaConfig.Background());
    }

    /**
     * 兼容旧用法：传一个本地目录。
     *
     * @param dir 本地图片目录，可为空；目录内没有图片时退回程序化生成
     */
    public BackgroundFactory(String dir) {
        this(backgroundWith(dir == null ? null : List.of(dir)));
    }

    /**
     * @param locations 任意数量的来源：目录 / 本地文件 / http(s) URL / classpath:资源
     */
    public BackgroundFactory(Collection<String> locations) {
        this(backgroundWith(locations));
    }

    /** 按 {@link CaptchaConfig.Background} 配置构建（starter 走的就是这个入口）。 */
    public BackgroundFactory(CaptchaConfig.Background config) {
        CaptchaConfig.Background cfg = config == null ? new CaptchaConfig.Background() : config;
        this.ratio = Math.max(0, Math.min(100, cfg.getRatio()));
        this.refreshMs = Math.max(0, cfg.getRefreshSeconds()) * 1000L;
        this.readTimeoutMs = Math.max(500, cfg.getReadTimeoutMs());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, cfg.getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        load(cfg.resolvedLocations());
    }

    private static CaptchaConfig.Background backgroundWith(Collection<String> locations) {
        CaptchaConfig.Background cfg = new CaptchaConfig.Background();
        if (locations != null) {
            cfg.setLocations(new ArrayList<>(locations));
        }
        return cfg;
    }

    // ------------------------------------------------------------------ 加载

    /** 重新解析所有配置来源（运行期替换目录内容后可调用）。 */
    public synchronized void reload() {
        List<String> keep = new ArrayList<>(configured);
        int external = load(keep);
        log.info("背景图已重新加载：" + external + " 张外部图片");
    }

    /**
     * 解析并替换当前来源列表。
     *
     * @return 成功解析到的图片来源数量
     */
    public synchronized int load(Collection<String> locations) {
        configured.clear();
        sources.clear();
        if (locations != null) {
            for (String raw : locations) {
                if (raw != null && !raw.isBlank() && !raw.trim().startsWith("#")) {
                    configured.add(raw.trim());
                }
            }
        }
        int files = 0;
        int remotes = 0;
        for (String location : configured) {
            for (ImageSource source : resolve(location)) {
                if (source.kind == ImageSource.Kind.REMOTE) {
                    remotes++;
                } else {
                    files++;
                }
                sources.add(source);
            }
        }

        if (sources.isEmpty()) {
            if (!configured.isEmpty()) {
                log.warning("没有解析到可用的背景图，将使用程序化背景：" + configured);
            }
            return 0;
        }
        log.info("已加载 " + sources.size() + " 张外部背景图（本地 " + files
                + " / 远程 " + remotes + "），使用概率 " + ratio + "%");
        prefetchRemotes();
        return sources.size();
    }

    private List<ImageSource> resolve(String location) {
        String value = expandHome(location);
        if (value.startsWith("classpath:")) {
            return resolveClasspath(value.substring("classpath:".length()));
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            try {
                URI uri = URI.create(value);
                if (uri.getHost() == null) {
                    log.warning("远程背景图地址缺少主机名，已忽略：" + value);
                    return List.of();
                }
                return List.of(remoteSource(value));
            } catch (IllegalArgumentException e) {
                log.warning("远程背景图地址非法，已忽略：" + value + " (" + e.getMessage() + ")");
                return List.of();
            }
        }
        if (lower.startsWith("file:")) {
            try {
                return resolvePath(Paths.get(URI.create(value)));
            } catch (IllegalArgumentException e) {
                log.warning("file: 地址非法，已忽略：" + value);
                return List.of();
            }
        }
        return resolvePath(Paths.get(value));
    }

    /** 目录（递归）或单个文件。 */
    private List<ImageSource> resolvePath(Path path) {
        if (!Files.exists(path)) {
            log.warning("背景图路径不存在，已忽略：" + path);
            return List.of();
        }
        if (Files.isRegularFile(path)) {
            if (!isImage(path.getFileName().toString())) {
                log.warning("不是受支持的图片格式，已忽略：" + path);
                return List.of();
            }
            return List.of(fileSource(path.toAbsolutePath().normalize()));
        }
        List<ImageSource> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(path, MAX_SCAN_DEPTH)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> isImage(p.getFileName().toString()))
                    .limit(MAX_IMAGES_PER_DIR)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> found.add(fileSource(p.toAbsolutePath().normalize())));
        } catch (IOException e) {
            log.warning("扫描背景图目录失败：" + path + " (" + e.getMessage() + ")");
            return List.of();
        }
        if (found.isEmpty()) {
            log.warning("目录内没有可用图片，已忽略：" + path);
        }
        return found;
    }

    /** classpath 资源：支持单文件、目录、以及 {@code dir/*} / {@code dir/**} 通配。 */
    private List<ImageSource> resolveClasspath(String path) {
        String clean = path.startsWith("/") ? path.substring(1) : path;
        boolean wildcard = clean.endsWith("/*") || clean.endsWith("/**");
        boolean recursive = clean.endsWith("/**");
        String base = wildcard ? clean.substring(0, clean.lastIndexOf('/')) : clean;
        if (!wildcard) {
            if (!isImage(clean)) {
                log.warning("classpath 背景图不是受支持的格式，已忽略：" + path);
                return List.of();
            }
            ClassLoader loader = loader();
            if (loader.getResource(clean) == null) {
                log.warning("classpath 背景图不存在，已忽略：" + path);
                return List.of();
            }
            return List.of(classpathSource(clean));
        }
        Set<String> hits = new LinkedHashSet<>();
        ClassLoader loader = loader();
        URL dir = loader.getResource(base);
        if (dir == null) {
            log.warning("classpath 背景图目录不存在，已忽略：" + path);
            return List.of();
        }
        try {
            if ("file".equals(dir.getProtocol())) {
                Path folder = Paths.get(dir.toURI());
                try (Stream<Path> walk = Files.walk(folder, recursive ? MAX_SCAN_DEPTH : 1)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> isImage(p.getFileName().toString()))
                            .sorted()
                            .limit(MAX_IMAGES_PER_DIR)
                            .forEach(p -> hits.add(base + "/" + folder.relativize(p).toString().replace('\\', '/')));
                }
            } else if ("jar".equals(dir.getProtocol())) {
                URLConnection connection = dir.openConnection();
                if (connection instanceof JarURLConnection jarConnection) {
                    String prefix = jarConnection.getEntryName();
                    if (!prefix.endsWith("/")) {
                        prefix = prefix + "/";
                    }
                    try (JarFile jar = jarConnection.getJarFile()) {
                        for (JarEntry entry : Collections.list(jar.entries())) {
                            String name = entry.getName();
                            if (entry.isDirectory() || !name.startsWith(prefix) || !isImage(name)) {
                                continue;
                            }
                            if (!recursive && name.indexOf('/', prefix.length()) >= 0) {
                                continue;
                            }
                            hits.add(name);
                            if (hits.size() >= MAX_IMAGES_PER_DIR) {
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warning("扫描 classpath 背景图目录失败：" + path + " (" + e.getMessage() + ")");
        }
        if (hits.isEmpty()) {
            log.warning("classpath 目录内没有可用图片，已忽略：" + path);
        }
        return hits.stream().map(this::classpathSource).toList();
    }

    private ImageSource fileSource(Path path) {
        return new ImageSource(ImageSource.Kind.FILE, path.toString(), path);
    }

    private ImageSource classpathSource(String resource) {
        return new ImageSource(ImageSource.Kind.CLASS_PATH, resource, null);
    }

    private ImageSource remoteSource(String url) {
        return new ImageSource(ImageSource.Kind.REMOTE, url, null);
    }

    private ClassLoader loader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : BackgroundFactory.class.getClassLoader();
    }

    private static String expandHome(String path) {
        if (path.equals("~")) {
            return System.getProperty("user.home", "~");
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return System.getProperty("user.home", "~") + path.substring(1);
        }
        return path;
    }

    /** 启动时后台预取远程图片，避免第一张验证码卡在下载上。 */
    private void prefetchRemotes() {
        List<ImageSource> remotes = sources.stream().filter(s -> s.kind == ImageSource.Kind.REMOTE).toList();
        if (remotes.isEmpty()) {
            return;
        }
        Thread thread = new Thread(() -> {
            for (ImageSource source : remotes) {
                source.loadBlocking();
            }
        }, "smart-captcha-bg-prefetch");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------ 读取

    /** 已加载的外部背景图数量。 */
    public int externalImageCount() {
        return sources.size();
    }

    /** 是否配置了外部来源（不代表图片一定能读出来）。 */
    public boolean hasExternalImages() {
        return !sources.isEmpty();
    }

    /** 供配置接口 / 日志展示的一句话摘要。 */
    public String describe() {
        long remote = sources.stream().filter(s -> s.kind == ImageSource.Kind.REMOTE).count();
        long file = sources.size() - remote;
        if (sources.isEmpty()) {
            return "程序化背景";
        }
        return "外部背景图 " + sources.size() + " 张（文件 " + file + " / 远程 " + remote + "），使用概率 " + ratio + "%";
    }

    /**
     * 取一张背景图：有外部素材时按 {@code ratio}% 概率使用，其余程序化生成。
     */
    public BufferedImage next(int width, int height, Random random) {
        if (!sources.isEmpty() && random.nextInt(100) < ratio) {
            BufferedImage loaded = loadExternal(random);
            if (loaded != null) {
                return ImageSupport.cover(loaded, width, height);
            }
        }
        return paint(width, height, random);
    }

    private BufferedImage loadExternal(Random random) {
        int size = sources.size();
        int start = random.nextInt(size);
        // 从随机位置开始遍历，个别图片坏掉也不至于整轮失败
        for (int i = 0; i < size; i++) {
            ImageSource source = sources.get((start + i) % size);
            BufferedImage image = source.load();
            if (image != null) {
                return image;
            }
        }
        return null;
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXT) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static BufferedImage decode(InputStream in) throws IOException {
        try (InputStream stream = in) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("无法识别的图片格式");
            }
            // 统一转成 RGB，避免带透明通道的图后续绘制异常
            if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    g.drawImage(image, 0, 0, null);
                } finally {
                    g.dispose();
                }
                return rgb;
            }
            return image;
        }
    }

    /** 一张已解析的背景图来源（本地文件 / classpath / 远程 URL）。 */
    private final class ImageSource {

        enum Kind {FILE, CLASS_PATH, REMOTE}

        private final Kind kind;
        private final String location;
        private final Path path;
        private final AtomicBoolean fetching = new AtomicBoolean();
        private volatile BufferedImage cache;
        private volatile long stamp = -1;
        private volatile long retryAt;

        private ImageSource(Kind kind, String location, Path path) {
            this.kind = kind;
            this.location = location;
            this.path = path;
        }

        BufferedImage load() {
            try {
                return switch (kind) {
                    case FILE -> loadFile();
                    case CLASS_PATH -> loadClasspath();
                    case REMOTE -> loadRemote(false);
                };
            } catch (Exception e) {
                log.warning("读取背景图失败：" + location + " (" + e.getMessage() + ")");
                return null;
            }
        }

        void loadBlocking() {
            if (kind == Kind.REMOTE) {
                try {
                    loadRemote(true);
                } catch (Exception e) {
                    log.warning("预取远程背景图失败：" + location + " (" + e.getMessage() + ")");
                }
            }
        }

        private BufferedImage loadFile() throws IOException {
            long modified = Files.getLastModifiedTime(path).toMillis();
            BufferedImage cached = cache;
            if (cached != null && modified == stamp) {
                return cached;
            }
            BufferedImage image = decode(Files.newInputStream(path));
            cache = image;
            stamp = modified;
            return image;
        }

        private BufferedImage loadClasspath() throws IOException {
            BufferedImage cached = cache;
            if (cached != null) {
                return cached;
            }
            ClassLoader loader = loader();
            try (InputStream in = loader.getResourceAsStream(location)) {
                if (in == null) {
                    throw new IOException("资源不存在");
                }
                BufferedImage image = decode(in);
                cache = image;
                stamp = System.currentTimeMillis();
                return image;
            }
        }

        /** @param block false 时若已有预取线程在下载，直接返回缓存，避免阻塞请求 */
        private BufferedImage loadRemote(boolean block) throws IOException, InterruptedException {
            long now = System.currentTimeMillis();
            BufferedImage cached = cache;
            boolean expired = cached == null || (refreshMs > 0 && now - stamp >= refreshMs);
            if (!expired) {
                return cached;
            }
            if (now < retryAt) {
                return cached;
            }
            if (!block && fetching.get()) {
                return cached;
            }
            if (!fetching.compareAndSet(false, true)) {
                return cached;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(location))
                        .timeout(Duration.ofMillis(readTimeoutMs))
                        .header("User-Agent", "SmartCaptcha/1.1 (+https://github.com/)")
                        .GET()
                        .build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                BufferedImage image = decode(new ByteArrayInputStream(response.body()));
                cache = image;
                stamp = System.currentTimeMillis();
                retryAt = 0;
                log.fine(() -> "远程背景图已缓存：" + location);
                return image;
            } catch (IOException | InterruptedException | RuntimeException e) {
                retryAt = System.currentTimeMillis() + REMOTE_RETRY_DELAY_MS;
                if (cached == null) {
                    log.warning("远程背景图加载失败，将退回程序化背景：" + location + " (" + e + ")");
                }
                throw e;
            } finally {
                fetching.set(false);
            }
        }
    }

    // ------------------------------------------------------------------ 程序化绘制

    /**
     * 程序化合成的抽象风景背景。
     */
    public BufferedImage paint(int width, int height, Random random) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        ImageSupport.applyQualityHints(g);
        try {
            Palette palette = PALETTES[random.nextInt(PALETTES.length)];

            // 1. 斜向主渐变
            g.setPaint(new GradientPaint(0, 0, palette.top(), width, height, palette.bottom()));
            g.fillRect(0, 0, width, height);
            g.setPaint(new GradientPaint(width, 0, ImageSupport.alpha(palette.mid(), 150),
                    0, height, ImageSupport.alpha(palette.mid(), 30)));
            g.fillRect(0, 0, width, height);

            // 2. 柔光团
            for (int i = 0; i < 3; i++) {
                float cx = random.nextFloat() * width;
                float cy = random.nextFloat() * height * 0.85f;
                float radius = (0.30f + random.nextFloat() * 0.45f) * width;
                RadialGradientPaint glow = new RadialGradientPaint(
                        new Point2D.Float(cx, cy), radius,
                        new float[]{0f, 0.55f, 1f},
                        new Color[]{ImageSupport.alpha(palette.glow(), 30 + random.nextInt(55)),
                                ImageSupport.alpha(palette.glow(), 18),
                                ImageSupport.alpha(palette.glow(), 0)},
                        MultipleGradientPaint.CycleMethod.NO_CYCLE);
                g.setPaint(glow);
                g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
            }

            // 3. 层叠山峦
            for (int layer = 0; layer < 3; layer++) {
                double baseY = height * (0.52 + layer * 0.16);
                Path2D.Double hill = new Path2D.Double();
                hill.moveTo(0, height);
                hill.lineTo(0, baseY);
                double step = width / 3.0;
                for (double x = 0; x < width + step; x += step) {
                    double next = x + step;
                    double peak = 12 + random.nextDouble() * 34;
                    hill.curveTo(
                            x + step * 0.32, baseY - peak,
                            next - step * 0.32, baseY + peak * 0.45,
                            next, baseY + (random.nextDouble() - 0.5) * 14
                    );
                }
                hill.lineTo(width, height);
                hill.closePath();
                g.setColor(ImageSupport.alpha(palette.hill(), 55 + layer * 48));
                g.fill(hill);
            }

            // 4. 斜向光带
            g.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < 14; i++) {
                int x = random.nextInt(width + width / 2) - width / 2;
                int y = random.nextInt(height);
                int len = 40 + random.nextInt(Math.max(40, width / 2));
                g.setColor(new Color(255, 255, 255, 12 + random.nextInt(26)));
                g.drawLine(x, y, x + len, y - len / 3);
            }

            // 5. 星点颗粒
            for (int i = 0; i < (width * height) / 420; i++) {
                int x = random.nextInt(width);
                int y = random.nextInt(height);
                int size = 1 + random.nextInt(2);
                g.setColor(new Color(255, 255, 255, 40 + random.nextInt(90)));
                g.fillOval(x, y, size, size);
            }

            // 6. 暗角
            RadialGradientPaint vignette = new RadialGradientPaint(
                    new Point2D.Float(width / 2f, height / 2f), Math.max(width, height) * 0.78f,
                    new float[]{0.45f, 1f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 110)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE);
            g.setPaint(vignette);
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.SrcOver);
        } finally {
            g.dispose();
        }
        return image;
    }
}
