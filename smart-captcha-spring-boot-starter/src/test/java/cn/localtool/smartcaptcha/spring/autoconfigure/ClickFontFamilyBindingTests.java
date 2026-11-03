package cn.localtool.smartcaptcha.spring.autoconfigure;

import cn.localtool.smartcaptcha.spring.SmartCaptchaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code smart-captcha.click.font-family / font-path} 绑定：字体族名与字体文件路径均可由 yaml 配置。
 */
@SpringBootTest(properties = {
        "smart-captcha.click.font-family=Monospaced",
        "smart-captcha.click.font-path=classpath:fonts/app.ttf",
})
class ClickFontFamilyBindingTests {

    @Autowired
    private SmartCaptchaProperties properties;

    @Test
    void fontFamilyAndPathAreBound() {
        assertEquals("Monospaced", properties.getClick().getFontFamily());
        assertEquals("classpath:fonts/app.ttf", properties.getClick().getFontPath());
    }

    @Test
    void fontObjectDefaultsToNull() {
        assertTrue(properties.getClick().getFont() == null,
                "Font 对象只能由代码设置，yaml 绑定不应产生默认值");
    }
}
