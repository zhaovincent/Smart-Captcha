package cn.localtool.smartcaptcha.server.web;

import cn.localtool.smartcaptcha.core.Coordinate;
import cn.localtool.smartcaptcha.core.HumanTrace;
import cn.localtool.smartcaptcha.core.VerifyInput;

import java.util.List;

/**
 * 校验请求。不同验证方式使用不同字段：
 * <ul>
 *     <li>slider：x（拼图块左边缘在原图中的横坐标），可选 trail 轨迹</li>
 *     <li>click：points（按提示顺序的点击坐标）</li>
 *     <li>rotate：angle（用户顺时针旋转角度，可为负值）</li>
 *     <li>human：pow.nonce（工作量证明解）+ trace（行为证据）</li>
 * </ul>
 *
 * <p>{@code trail} / {@code y} 目前仅透传给风控侧，内核不参与判定；{@code trace.straight}
 * 同理，只用于日志与二次风控。</p>
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
        List<Coordinate> trail,
        Pow pow,
        Trace trace
) {

    /** 工作量证明解。 */
    public record Pow(Long nonce) {
    }

    /**
     * 行为证据。字段都做包装类型 + 空值兜底：前端少传一个字段不应该变成 500。
     */
    public record Trace(Integer moves, Integer clicks, Integer keys, Double distance, Double straight, String pointer) {

        public HumanTrace toTrace() {
            return HumanTrace.of(
                    moves == null ? 0 : moves,
                    clicks == null ? 0 : clicks,
                    keys == null ? 0 : keys,
                    distance == null ? 0 : distance,
                    straight == null ? 0 : straight,
                    pointer);
        }
    }

    /** 转成内核的校验入参。 */
    public VerifyInput toInput() {
        return new VerifyInput(id, token, type, x, angle, points, duration,
                pow == null ? null : pow.nonce(),
                trace == null ? null : trace.toTrace());
    }
}
