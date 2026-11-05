package cn.localtool.smartcaptcha.core;

import java.util.List;

/**
 * 校验输入。不同验证方式使用不同字段：
 * <ul>
 *     <li>slider：{@code x} —— 拼图块左边缘在原图中的横坐标；</li>
 *     <li>click：{@code points} —— 按提示顺序的点击坐标；</li>
 *     <li>rotate：{@code angle} —— 用户顺时针旋转的角度，可为负值；</li>
 *     <li>human：{@code nonce} —— 工作量证明解，{@code trace} —— 行为证据。</li>
 * </ul>
 *
 * <p>用静态工厂构造，避免调用方混淆字段：</p>
 * <pre>{@code
 * engine.verify(VerifyInput.slider(id, token, 213.5));
 * engine.verify(VerifyInput.click(id, token, points));
 * engine.verify(VerifyInput.rotate(id, token, 96.5));
 * engine.verify(VerifyInput.human(id, token, nonce, trace));
 * }</pre>
 */
public record VerifyInput(
        String id,
        String token,
        String type,
        Double x,
        Double angle,
        List<Coordinate> points,
        Long duration,
        Long nonce,
        HumanTrace trace
) {

    public static VerifyInput slider(String id, String token, double x) {
        return new VerifyInput(id, token, CaptchaType.SLIDER.getCode(), x, null, null, null, null, null);
    }

    public static VerifyInput click(String id, String token, List<Coordinate> points) {
        return new VerifyInput(id, token, CaptchaType.CLICK.getCode(), null, null, points, null, null, null);
    }

    public static VerifyInput rotate(String id, String token, double angle) {
        return new VerifyInput(id, token, CaptchaType.ROTATE.getCode(), null, angle, null, null, null, null);
    }

    /**
     * 人机验证。
     *
     * @param nonce 工作量证明解（服务端会自己重算摘要，不信任客户端给的哈希）
     * @param trace 行为证据，可为 null（服务端未开启轨迹校验时）
     */
    public static VerifyInput human(String id, String token, Long nonce, HumanTrace trace) {
        return new VerifyInput(id, token, CaptchaType.HUMAN.getCode(), null, null, null, null, nonce, trace);
    }

    /** 设置作答耗时（毫秒），配合 {@code minDurationMs} 拦截脚本瞬时提交。 */
    public VerifyInput withDuration(Long duration) {
        return new VerifyInput(id, token, type, x, angle, points, duration, nonce, trace);
    }
}
