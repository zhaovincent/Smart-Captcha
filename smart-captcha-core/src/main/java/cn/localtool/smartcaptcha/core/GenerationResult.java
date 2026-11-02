package cn.localtool.smartcaptcha.core;

import java.awt.image.BufferedImage;

/**
 * 单个生成器的产物：最终图像 + 前端渲染信息 + 服务端答案。
 */
public record GenerationResult(
        BufferedImage image,
        int width,
        int height,
        CaptchaAnswer answer,
        CaptchaChallenge.SliderChallenge slider,
        CaptchaChallenge.ClickChallenge click,
        CaptchaChallenge.RotateChallenge rotate,
        String tip
) {

    public static GenerationResult of(BufferedImage image, CaptchaAnswer answer,
                                      CaptchaChallenge.SliderChallenge slider, String tip) {
        return new GenerationResult(image, image.getWidth(), image.getHeight(), answer, slider, null, null, tip);
    }

    public static GenerationResult of(BufferedImage image, CaptchaAnswer answer,
                                      CaptchaChallenge.ClickChallenge click, String tip) {
        return new GenerationResult(image, image.getWidth(), image.getHeight(), answer, null, click, null, tip);
    }

    public static GenerationResult of(BufferedImage image, CaptchaAnswer answer,
                                      CaptchaChallenge.RotateChallenge rotate, String tip) {
        return new GenerationResult(image, image.getWidth(), image.getHeight(), answer, null, null, rotate, tip);
    }
}
