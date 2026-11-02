package cn.localtool.smartcaptcha.core;

/**
 * 校验结果。success=true 时返回 ticket，业务接口可再用 ticket 做二次校验。
 */
public record VerifyResult(boolean success, String status, String message, String ticket, Integer attemptsLeft) {

    public static VerifyResult pass(String ticket) {
        return new VerifyResult(true, "PASS", "验证通过", ticket, null);
    }

    public static VerifyResult fail(String message, Integer attemptsLeft) {
        return new VerifyResult(false, "FAIL", message, null, attemptsLeft);
    }
}
