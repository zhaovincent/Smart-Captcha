package cn.localtool.smartcaptcha.core;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证方式枚举。
 */
public enum CaptchaType {

    /** 滑块拼图。 */
    SLIDER("slider"),
    /** 文字点选。 */
    CLICK("click"),
    /** 角度旋转。 */
    ROTATE("rotate"),
    /**
     * 一键人机验证：用户只点一下，服务端靠「工作量证明 + 行为证据 + 时序」判定。
     * 摩擦最小，适合作为第一层防护；高风险场景应对失败次数多的来源回退到滑块 / 点选。
     */
    HUMAN("human");

    private final String code;

    CaptchaType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static Optional<CaptchaType> of(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String v = value.trim().toLowerCase();
        return Arrays.stream(values()).filter(t -> t.code.equals(v)).findFirst();
    }

    /** 从候选池中随机取一种。 */
    public static CaptchaType randomOf(List<CaptchaType> pool) {
        List<CaptchaType> list = (pool == null || pool.isEmpty()) ? List.of(values()) : pool;
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
