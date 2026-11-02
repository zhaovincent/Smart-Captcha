package cn.localtool.smartcaptcha.server.web;

/**
 * 统一返回包装（本服务自己的契约，starter 不再提供）。
 *
 * <p>code：0 表示请求成功（注意：验证码校验接口 code=0 也不代表验证通过，需看 data.success）；
 * 4001 参数错误；4002 验证码不存在或已过期；4003 尝试次数耗尽；4004 ticket 无效；5000 服务异常。</p>
 */
public record ApiResult<T>(int code, boolean success, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, true, "ok", data);
    }

    public static <T> ApiResult<T> ok(String message, T data) {
        return new ApiResult<>(0, true, message, data);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, false, message, null);
    }
}
