package cn.localtool.smartcaptcha.core;

import java.util.List;

/**
 * 校验输入。不同验证方式使用不同字段：
 * <ul>
 *     <li>slider：{@code x} —— 拼图块左边缘在原图中的横坐标；</li>
 *     <li>click：{@code points} —— 按提示顺序的点击坐标；</li>
 *     <li>rotate：{@code angle} —— 用户顺时针旋转的角度，可为负值。</li>
 * </ul>
 *
 * <p>用静态工厂构造，避免调用方混淆字段：</p>
 * <pre>{@code
 * engine.verify(VerifyInput.slider(id, token, 213.5));
 * engine.verify(VerifyInput.click(id, token, points));
 * engine.verify(VerifyInput.rotate(id, token, 96.5));
 * }</pre>
 */
public record VerifyInput(
        String id,
        String token,
        String type,
        Double x,
        Double angle,
        List<Coordinate> points,
        Long duration
) {

    public static VerifyInput slider(String id, String token, double x) {
        return new VerifyInput(id, token, CaptchaType.SLIDER.getCode(), x, null, null, null);
    }

    public static VerifyInput click(String id, String token, List<Coordinate> points) {
        return new VerifyInput(id, token, CaptchaType.CLICK.getCode(), null, null, points, null);
    }

    public static VerifyInput rotate(String id, String token, double angle) {
        return new VerifyInput(id, token, CaptchaType.ROTATE.getCode(), null, angle, null, null);
    }

    /** 设置作答耗时（毫秒），配合 {@code minDurationMs} 拦截脚本瞬时提交。 */
    public VerifyInput withDuration(Long duration) {
        return new VerifyInput(id, token, type, x, angle, points, duration);
    }
}
