package cn.localtool.smartcaptcha.server.web;

/**
 * 生成验证码请求。type 支持 slider/click/rotate/auto，留空或 auto 表示随机。
 * backgroundImage 支持外部背景图（dataURL 或纯 base64），留空则由服务端自动生成。
 * clickCharset 可选，覆盖 {@code smart-captcha.click.charset}（zh / en），仅对文字点选生效。
 */
public record GenerateRequest(String type, String backgroundImage, String clickCharset) {

    /** 兼容只传类型 / 背景图的调用方。 */
    public GenerateRequest(String type, String backgroundImage) {
        this(type, backgroundImage, null);
    }
}
