package cn.localtool.smartcaptcha.server.web;

import cn.localtool.smartcaptcha.core.Coordinate;
import cn.localtool.smartcaptcha.core.VerifyInput;

import java.util.List;

/**
 * 校验请求。不同验证方式使用不同字段：
 * <ul>
 *     <li>slider：x（拼图块左边缘在原图中的横坐标），可选 trail 轨迹</li>
 *     <li>click：points（按提示顺序的点击坐标）</li>
 *     <li>rotate：angle（用户顺时针旋转角度，可为负值）</li>
 * </ul>
 *
 * <p>{@code trail} / {@code y} 目前仅透传给风控侧，内核不参与判定。</p>
 */
public record VerifyRequest(
        String id,
        String token,
        String type,
        Double x,
        Double y,
        Double angle,
        List<Coordinate> points,
        Long duration,
        List<Coordinate> trail
) {

    /** 转成内核的校验入参。 */
    public VerifyInput toInput() {
        return new VerifyInput(id, token, type, x, angle, points, duration);
    }
}
