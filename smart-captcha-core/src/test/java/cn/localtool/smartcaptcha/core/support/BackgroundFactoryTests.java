package cn.localtool.smartcaptcha.core.support;

import cn.localtool.smartcaptcha.core.CaptchaConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 背景图来源解析测试：目录 / 多文件路径 / 远程 URL / classpath 资源，以及各种失败降级。
 */
class BackgroundFactoryTests {

    private static final Color MAGENTA = new Color(0xFF, 0x00, 0xFF);

    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // ------------------------------------------------------------------ 本地来源

    @Test
    void loadsDirectoryRecursively() throws IOException {
        Path folder = Files.createDirectory(tempDir.resolve("bg"));
        writeImage(folder.resolve("a.png"), Color.RED);
        writeImage(folder.resolve("b.jpg"), Color.GREEN);
        Files.writeString(folder.resolve("notes.txt"), "不是图片");
        Path nested = Files.createDirectory(folder.resolve("sub"));
        writeImage(nested.resolve("c.png"), Color.BLUE);

        BackgroundFactory factory = new BackgroundFactory(folder.toString());

        assertEquals(3, factory.externalImageCount());
        assertTrue(factory.describe().contains("外部背景图 3 张"), factory.describe());
    }

    @Test
    void loadsMultipleFilePathsAndSkipsInvalidOnes() throws IOException {
        Path one = tempDir.resolve("one.png");
        Path two = tempDir.resolve("two.png");
        writeImage(one, Color.ORANGE);
        writeImage(two, Color.CYAN);
        Path text = Files.writeString(tempDir.resolve("readme.txt"), "x");

        CaptchaConfig.Background config = new CaptchaConfig.Background();
        config.setLocations(java.util.List.of(
                one.toString(),
                "/definitely/not/here.png",
                text.toString(),
                two.toString()));

        BackgroundFactory factory = new BackgroundFactory(config);

        assertEquals(2, factory.externalImageCount());
    }

    @Test
    void directoryStringConstructorStillWorks() throws IOException {
        Path folder = Files.createDirectory(tempDir.resolve("legacy"));
        writeImage(folder.resolve("a.png"), Color.RED);

        BackgroundFactory factory = new BackgroundFactory(folder.toString());
        assertEquals(1, factory.externalImageCount());
    }

    @Test
    void reloadPicksUpNewFiles() throws IOException {
        Path folder = Files.createDirectory(tempDir.resolve("hot"));
        writeImage(folder.resolve("a.png"), Color.RED);
        CaptchaConfig.Background config = new CaptchaConfig.Background();
        config.setLocations(List.of(folder.toString()));
        BackgroundFactory factory = new BackgroundFactory(config);
        assertEquals(1, factory.externalImageCount());

        writeImage(folder.resolve("b.png"), Color.GREEN);
        factory.reload();

        assertEquals(2, factory.externalImageCount());
    }

    @Test
    void missingLocationFallsBackToProcedural() {
        CaptchaConfig.Background config = new CaptchaConfig.Background();
        config.setLocations(List.of(tempDir.resolve("nope").toString(), "  "));
        BackgroundFactory factory = new BackgroundFactory(config);

        assertEquals(0, factory.externalImageCount());
        assertEquals("程序化背景", factory.describe());
        BufferedImage image = factory.next(320, 180, new Random(7));
        assertEquals(320, image.getWidth());
    }

    // ------------------------------------------------------------------ classpath

    @Test
    void loadsClasspathSingleResource() {
        CaptchaConfig.Background config = new CaptchaConfig.Background();
        config.setLocations(List.of("classpath:captcha-bg/alpha.png"));

        assertEquals(1, new BackgroundFactory(config).externalImageCount());
    }

    @Test
    void classpathWildcardIsNotRecursiveButDoubleStarIs() {
        CaptchaConfig.Background flat = new CaptchaConfig.Background();
        flat.setLocations(List.of("classpath:captcha-bg/*"));
        assertEquals(2, new BackgroundFactory(flat).externalImageCount());

        CaptchaConfig.Background recursive = new CaptchaConfig.Background();
        recursive.setLocations(List.of("classpath:captcha-bg/**"));
        assertEquals(3, new BackgroundFactory(recursive).externalImageCount());
    }

    @Test
    void unknownClasspathResourceIsIgnored() {
        CaptchaConfig.Background config = new CaptchaConfig.Background();
        config.setLocations(List.of("classpath:captcha-bg/missing.png"));
        assertEquals(0, new BackgroundFactory(config).externalImageCount());
    }

    // ------------------------------------------------------------------ 远程 URL

    @Test
    void loadsRemoteImageAndCachesIt() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        startServer(hits);

        CaptchaConfig.Background config = new CaptchaConfig.Background();
        config.setLocations(List.of(url()));
        config.setRatio(100);
        BackgroundFactory factory = new BackgroundFactory(config);

        assertTrue(awaitExternal(factory), "远程背景图应能被取到并使用");
        int afterWarmUp = hits.get();
        assertTrue(afterWarmUp >= 1);

        // 缓存生效：继续取用不应再打后端
        for (int i = 0; i < 5; i++) {
            assertEquals(MAGENTA.getRGB(), factory.next(200, 100, new Random(i)).getRGB(10, 10));
        }
        assertEquals(afterWarmUp, hits.get(), "远程图片应当被缓存，不应重复下载");
    }

    @Test
    void unreachableRemoteFallsBackToProcedural() {
        CaptchaConfig.Background config = new CaptchaConfig.Background();
        config.setLocations(List.of("http://127.0.0.1:1/bg.png"));
        config.setRatio(100);
        config.setConnectTimeoutMs(300);
        config.setReadTimeoutMs(300);

        BackgroundFactory factory = new BackgroundFactory(config);
        assertTrue(factory.hasExternalImages());
        BufferedImage image = factory.next(320, 180, new Random(1));
        assertNotNull(image);
        assertEquals(320, image.getWidth());
        assertNotEquals(MAGENTA.getRGB(), image.getRGB(10, 10));
    }

    @Test
    void invalidRemoteUrlIsIgnored() {
        CaptchaConfig.Background config = new CaptchaConfig.Background();
        config.setLocations(List.of("http://", "htp:/oops"));
        assertEquals(0, new BackgroundFactory(config).externalImageCount());
    }

    // ------------------------------------------------------------------ 概率

    @Test
    void ratioZeroNeverUsesExternalImage() throws IOException {
        Path folder = Files.createDirectory(tempDir.resolve("ratio"));
        writeImage(folder.resolve("magenta.png"), MAGENTA);

        CaptchaConfig.Background config = new CaptchaConfig.Background();
        config.setLocations(List.of(folder.toString()));
        config.setRatio(0);
        BackgroundFactory factory = new BackgroundFactory(config);

        for (int i = 0; i < 12; i++) {
            assertNotEquals(MAGENTA.getRGB(), factory.next(200, 100, new Random(i)).getRGB(10, 10));
        }
        assertTrue(factory.describe().contains("使用概率 0%"), factory.describe());
    }

    @Test
    void ratioHundredAlwaysUsesExternalImage() throws IOException {
        Path folder = Files.createDirectory(tempDir.resolve("always"));
        writeImage(folder.resolve("magenta.png"), MAGENTA);

        CaptchaConfig.Background config = new CaptchaConfig.Background();
        config.setLocations(List.of(folder.toString()));
        config.setRatio(100);
        BackgroundFactory factory = new BackgroundFactory(config);

        for (int i = 0; i < 6; i++) {
            assertEquals(MAGENTA.getRGB(), factory.next(200, 100, new Random(i)).getRGB(10, 10));
        }
    }

    // ------------------------------------------------------------------ 工具

    private void startServer(AtomicInteger hits) throws IOException {
        byte[] png = pngBytes(MAGENTA);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bg.png", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, png.length);
            try (var out = exchange.getResponseBody()) {
                out.write(png);
            }
        });
        server.start();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/bg.png";
    }

    private boolean awaitExternal(BackgroundFactory factory) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (factory.next(200, 100, new Random(i)).getRGB(10, 10) == MAGENTA.getRGB()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static byte[] pngBytes(Color color) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(solid(color), "png", out);
        return out.toByteArray();
    }

    private static BufferedImage solid(Color color) {
        BufferedImage image = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
        return image;
    }

    private static void writeImage(Path file, Color color) throws IOException {
        ImageIO.write(solid(color), file.toString().endsWith(".jpg") ? "jpg" : "png", file.toFile());
    }
}
