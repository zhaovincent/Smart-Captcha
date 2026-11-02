package cn.localtool.smartcaptcha.core;

import java.util.List;

/**
 * 服务端保存的答案，绝不返回给前端。
 */
public record CaptchaAnswer(Double targetX, Double pieceWidth, List<double[]> points, Double targetAngle) {

    public static CaptchaAnswer slider(double targetX, double pieceWidth) {
        return new CaptchaAnswer(targetX, pieceWidth, null, null);
    }

    public static CaptchaAnswer click(List<double[]> points) {
        return new CaptchaAnswer(null, null, points, null);
    }

    public static CaptchaAnswer rotate(double targetAngle) {
        return new CaptchaAnswer(null, null, null, targetAngle);
    }
}
