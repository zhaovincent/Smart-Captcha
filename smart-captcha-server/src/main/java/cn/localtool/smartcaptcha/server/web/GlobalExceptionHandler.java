package cn.localtool.smartcaptcha.server.web;

import cn.localtool.smartcaptcha.core.CaptchaException;
import cn.localtool.smartcaptcha.spring.web.CaptchaTicketException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理，保证前端永远拿到 {code, success, message, data} 结构。
 *
 * <p>starter 的 {@code CaptchaInterceptor} 只抛 {@link CaptchaTicketException}，
 * 具体的错误体在这里拼——换一个项目接入时，按自己团队的统一结构写一遍即可。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CaptchaException.class)
    public ApiResult<Void> handleCaptcha(CaptchaException e) {
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    /** 缺少 / 过期 / 已使用 ticket：HTTP 403 + code 4004。 */
    @ExceptionHandler(CaptchaTicketException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResult<Void> handleTicket(CaptchaTicketException e) {
        return ApiResult.fail(CaptchaTicketException.CODE, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResult<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return ApiResult.fail(4001, "请求体格式错误");
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception e) {
        log.error("captcha server error", e);
        return ApiResult.fail(5000, "服务异常，请稍后重试");
    }
}
