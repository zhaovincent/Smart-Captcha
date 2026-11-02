package cn.localtool.smartcaptcha.spring.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 测试用业务接口：开启拦截器时没有 ticket 必须被拦。 */
@RestController
class GuardedTestController {

    @RequireCaptcha
    @PostMapping("/test/guarded")
    String guarded() {
        return "ok";
    }

    @PostMapping("/test/open")
    String open() {
        return "ok";
    }
}
