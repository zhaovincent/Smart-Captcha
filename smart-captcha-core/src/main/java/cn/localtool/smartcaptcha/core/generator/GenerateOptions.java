package cn.localtool.smartcaptcha.core.generator;

/**
 * 单次生成的覆盖项：字段为 null 时使用全局配置。
 *
 * @param clickCharset 文字点选字符集 zh / en，覆盖 {@code click.charset}
 */
public record GenerateOptions(String clickCharset) {

    public static final GenerateOptions DEFAULT = new GenerateOptions(null);

    public static GenerateOptions of(String clickCharset) {
        return clickCharset == null || clickCharset.isBlank() ? DEFAULT : new GenerateOptions(clickCharset);
    }
}
