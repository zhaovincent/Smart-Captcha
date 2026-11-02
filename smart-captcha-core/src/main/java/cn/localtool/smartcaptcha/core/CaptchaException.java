package cn.localtool.smartcaptcha.core;

/**
 * 验证码业务异常。
 *
 * <p>code 约定：4001 参数错误 / 4002 验证码不存在或已过期 / 4003 尝试次数耗尽 / 4004 ticket 无效。
 * HTTP 层可以据此映射状态码与响应体。</p>
 */
public class CaptchaException extends RuntimeException {

    private final int code;

    public CaptchaException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
