package cn.localtool.smartcaptcha.core;

import java.util.List;

/**
 * 服务端保存的答案，绝不返回给前端。
 */
public record CaptchaAnswer(Double targetX, Double pieceWidth, List<double[]> points, Double targetAngle, Human human) {

    public static CaptchaAnswer slider(double targetX, double pieceWidth) {
        return new CaptchaAnswer(targetX, pieceWidth, null, null, null);
    }

    public static CaptchaAnswer click(List<double[]> points) {
        return new CaptchaAnswer(null, null, points, null, null);
    }

    public static CaptchaAnswer rotate(double targetAngle) {
        return new CaptchaAnswer(null, null, null, targetAngle, null);
    }

    public static CaptchaAnswer human(Human human) {
        return new CaptchaAnswer(null, null, null, null, human);
    }

    /**
     * 人机验证的判题参数。生成时从配置快照下来存进会话，
     * 这样运行期改配置也不会影响已经在途的验证码。
     *
     * @param salt          随机盐
     * @param difficulty    工作量证明难度（前导 0 的二进制位），0 表示不要求 PoW
     * @param minDurationMs 最短作答耗时
     * @param minMoves      要求的最少指针采样数
     * @param requireTrace  是否要求存在真实交互痕迹
     */
    public record Human(String salt, int difficulty, long minDurationMs, int minMoves, boolean requireTrace) {
    }
}
