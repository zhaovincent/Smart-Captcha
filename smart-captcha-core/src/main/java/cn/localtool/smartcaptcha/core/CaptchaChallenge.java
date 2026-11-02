package cn.localtool.smartcaptcha.core;

import java.util.List;

/**
 * 生成验证码的返回体。前端只需按 type 渲染对应字段。
 *
 * <p>注意：任何情况下都不会返回答案字段。</p>
 */
public record CaptchaChallenge(
        String id,
        String token,
        String type,
        int width,
        int height,
        /** 背景图 dataURL（滑块为带缺口的背景，点选为带文字的背景，旋转为圆形拼图本身）。 */
        String image,
        SliderChallenge slider,
        ClickChallenge click,
        RotateChallenge rotate,
        int expireIn,
        String tip
) {

    /** 滑块：可拖动的拼图块。 */
    public record SliderChallenge(String piece, int pieceWidth, int pieceHeight, int y) {
    }

    /** 点选：需要依次点击的文字（按顺序），charset 为实际使用的字符集 zh / en。 */
    public record ClickChallenge(List<String> words, String charset) {
    }

    /** 旋转：圆盘尺寸。 */
    public record RotateChallenge(int size) {
    }
}
