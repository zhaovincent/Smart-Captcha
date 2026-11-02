package cn.localtool.smartcaptcha.core.generator;

import cn.localtool.smartcaptcha.core.CaptchaConfig;
import cn.localtool.smartcaptcha.core.CaptchaType;
import cn.localtool.smartcaptcha.core.GenerationResult;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 验证码生成器 SPI。
 *
 * <p>新增一种验证方式只需：</p>
 * <ol>
 *     <li>实现本接口；</li>
 *     <li>注册给引擎 —— {@code CaptchaEngine.builder(config).generator(new MyGenerator()).build()}，
 *         或者放进 {@code META-INF/services/cn.localtool.smartcaptcha.core.generator.CaptchaGenerator}
 *         由 {@link java.util.ServiceLoader} 自动发现；</li>
 *     <li>Spring Boot 环境下注册成 Bean 也会被自动收集，无需改任何配置。</li>
 * </ol>
 */
public interface CaptchaGenerator {

    CaptchaType type();

    /**
     * @param background 已按配置尺寸裁剪好的背景图
     * @param config     全局配置
     * @param random     随机源
     */
    GenerationResult generate(BufferedImage background, CaptchaConfig config, Random random);

    /**
     * 带单次覆盖项的生成入口。默认忽略 options，需要支持覆盖的生成器自行重写。
     */
    default GenerationResult generate(BufferedImage background, CaptchaConfig config,
                                      Random random, GenerateOptions options) {
        return generate(background, config, random);
    }
}
