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
        /** 背景图 dataURL（滑块为带缺口的背景，点选为带文字的背景，旋转为圆形拼图本身，人机验证为 null）。 */
        String image,
        SliderChallenge slider,
        ClickChallenge click,
        RotateChallenge rotate,
        HumanChallenge human,
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

    /**
     * 人机验证：前端拿到这些参数后在本地算工作量证明。
     *
     * <p>题目是 {@code SHA-256(domain + "|" + token + "|" + salt + "|" + nonce)} 的前
     * {@code difficulty} 位为 0。{@code token} 由外层字段提供，天然把解绑死在这一张验证码上。</p>
     *
     * @param algorithm     摘要算法（固定 SHA-256）
     * @param domain        哈希输入的领域前缀，前端按 {@code domain|token|salt|nonce} 拼接
     * @param salt          随机盐
     * @param difficulty    前导 0 位数，0 表示服务端关闭了 PoW
     * @param minDurationMs 最短作答耗时（毫秒）
     * @param minMoves      期望的最少指针采样数
     * @param requireTrace  是否要求上报行为证据
     */
    public record HumanChallenge(String algorithm, String domain, String salt, int difficulty,
                                 int minDurationMs, int minMoves, boolean requireTrace) {

        /** 拼出哈希前缀，服务端与前端必须用同样的拼法。 */
        public String hashPrefix(String token) {
            return (domain == null ? "" : domain) + "|" + token + "|" + salt + "|";
        }
    }
}
