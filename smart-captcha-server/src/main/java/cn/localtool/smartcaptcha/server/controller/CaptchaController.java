package cn.localtool.smartcaptcha.server.controller;

import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaEngine;
import cn.localtool.smartcaptcha.core.VerifyResult;
import cn.localtool.smartcaptcha.server.web.GenerateRequest;
import cn.localtool.smartcaptcha.server.web.VerifyRequest;
import cn.localtool.smartcaptcha.server.web.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 验证码接口（供前端 Vue 组件通过 @request / @verify 事件调用）。
 *
 * <ul>
 *     <li>GET  /api/captcha/config   —— 前端读取可用方式 / 尺寸 / 有效期等</li>
 *     <li>POST /api/captcha/generate —— 生成验证码（可指定类型、可传外部背景图）</li>
 *     <li>GET  /api/captcha/generate —— 生成验证码（简单场景，?type=slider|click|rotate|auto）</li>
 *     <li>POST /api/captcha/verify   —— 校验用户行为</li>
 *     <li>POST /api/captcha/ticket/verify —— 业务侧校验 ticket（一次性）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {

    private final CaptchaEngine engine;

    public CaptchaController(CaptchaEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/config")
    public ApiResult<Map<String, Object>> config() {
        return ApiResult.ok(engine.publicConfig());
    }

    @PostMapping("/generate")
    public ApiResult<CaptchaChallenge> generate(@RequestBody(required = false) GenerateRequest request) {
        return ApiResult.ok(engine.generate(
                request == null ? null : request.type(),
                request == null ? null : request.backgroundImage(),
                request == null ? null : request.clickCharset()));
    }

    @GetMapping("/generate")
    public ApiResult<CaptchaChallenge> generateByGet(@RequestParam(required = false) String type,
                                                     @RequestParam(required = false) String clickCharset) {
        return ApiResult.ok(engine.generate(type, null, clickCharset));
    }

    @PostMapping("/verify")
    public ApiResult<VerifyResult> verify(@RequestBody VerifyRequest request) {
        return ApiResult.ok(engine.verify(request.toInput()));
    }

    @PostMapping("/ticket/verify")
    public ApiResult<Map<String, Object>> verifyTicket(@RequestBody Map<String, String> body) {
        String ticket = body == null ? null : body.get("ticket");
        boolean valid = engine.consumeTicket(ticket);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", valid);
        data.put("message", valid ? "ticket 有效（已消费）" : "ticket 无效或已使用");
        return ApiResult.ok(data);
    }
}
