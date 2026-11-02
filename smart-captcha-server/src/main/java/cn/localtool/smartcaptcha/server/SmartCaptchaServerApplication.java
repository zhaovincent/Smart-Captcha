package cn.localtool.smartcaptcha.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SmartCaptcha 服务启动类（Spring Boot 3.x）。
 *
 * <p>验证码能力全部来自 {@code smart-captcha-spring-boot-starter}，这里只提供 HTTP 接口
 * 与演示用的业务接口。</p>
 */
@SpringBootApplication
public class SmartCaptchaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCaptchaServerApplication.class, args);
    }

}
