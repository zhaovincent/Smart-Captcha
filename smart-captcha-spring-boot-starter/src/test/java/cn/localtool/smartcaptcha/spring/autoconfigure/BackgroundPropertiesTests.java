package cn.localtool.smartcaptcha.spring.autoconfigure;

import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.core.support.BackgroundFactory;
import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code smart-captcha.background.*} 的绑定与多来源解析。 */
class BackgroundPropertiesTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MemoryCaptchaStoreAutoConfiguration.class,
                    SmartCaptchaAutoConfiguration.class));

    @TempDir
    Path tempDir;

    @Test
    void bindsLocationsListAndRatio() {
        runner.withPropertyValues(
                        "smart-captcha.background.locations[0]=https://cdn.example.com/a.png",
                        "smart-captcha.background.locations[1]=classpath:captcha-bg/alpha.png",
                        "smart-captcha.background.ratio=80",
                        "smart-captcha.background.refresh-seconds=600",
                        "smart-captcha.background.connect-timeout-ms=1500",
                        "smart-captcha.background.read-timeout-ms=2500")
                .run(context -> {
                    SmartCaptchaProperties props = context.getBean(SmartCaptchaProperties.class);
                    assertThat(props.getBackground().getLocations()).hasSize(2);
                    assertThat(props.getBackground().getRatio()).isEqualTo(80);
                    assertThat(props.getBackground().getRefreshSeconds()).isEqualTo(600);
                    assertThat(props.getBackground().getConnectTimeoutMs()).isEqualTo(1500);
                    assertThat(props.getBackground().getReadTimeoutMs()).isEqualTo(2500);
                });
    }

    @Test
    void legacyDirPropertyIsMergedIntoLocations() {
        runner.withPropertyValues("smart-captcha.background.dir=/data/bg")
                .run(context -> assertThat(context.getBean(SmartCaptchaProperties.class)
                        .getBackground().resolvedLocations()).containsExactly("/data/bg"));
    }

    @Test
    void engineUsesConfiguredLocations() throws IOException {
        Path folder = Files.createDirectories(tempDir.resolve("bg"));
        writeImage(folder.resolve("a.png"));
        writeImage(folder.resolve("b.png"));

        runner.withPropertyValues("smart-captcha.background.locations[0]=" + folder)
                .run(context -> {
                    CaptchaEngine engine = context.getBean(CaptchaEngine.class);
                    assertThat(engine.backgrounds().externalImageCount()).isEqualTo(2);
                    assertThat(engine.publicConfig().get("backgroundImages")).isEqualTo(2);
                    assertThat(String.valueOf(engine.publicConfig().get("background")))
                            .contains("外部背景图 2 张");
                });
    }

    @Test
    void customBackgroundFactoryBeanWins() {
        BackgroundFactory custom = new BackgroundFactory();
        runner.withBean("customBackgrounds", BackgroundFactory.class, () -> custom)
                .run(context -> assertThat(context.getBean(CaptchaEngine.class).backgrounds())
                        .isSameAs(custom));
    }

    private static void writeImage(Path file) throws IOException {
        BufferedImage image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, 80, 40);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
    }
}
