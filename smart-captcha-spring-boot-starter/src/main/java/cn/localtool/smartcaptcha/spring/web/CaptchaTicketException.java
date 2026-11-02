package cn.localtool.smartcaptcha.spring.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ticket 校验失败（缺失 / 过期 / 已使用）时由 {@link CaptchaInterceptor} 抛出。
 *
 * <p>starter 只负责"判断不通过就抛异常"，<b>不决定响应体长什么样</b>：</p>
 * <ul>
 *   <li>标了 {@code @ResponseStatus(FORBIDDEN)}，接入方什么都不做时也会得到 403；</li>
 *   <li>接入方有 {@code @RestControllerAdvice} 时，捕获本异常即可输出自己的统一结构
 *       （例如 {@code {"code":4004,"success":false,"message":"…"}}）；</li>
 *   <li>{@link #getHeader()} 告诉你该接口期望从哪个请求头读 ticket，便于在响应里提示前端。</li>
 * </ul>
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class CaptchaTicketException extends RuntimeException {

    /** 建议的错误码：前端据此提示"重新验证"。 */
    public static final int CODE = 4004;

    private final String header;

    public CaptchaTicketException(String message, String header) {
        super(message);
        this.header = header;
    }

    /** 该接口读取 ticket 的请求头名称（来自 {@link RequireCaptcha#header()}）。 */
    public String getHeader() {
        return header;
    }
}
