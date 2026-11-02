package cn.localtool.smartcaptcha.core;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * 验证码内核配置（纯 POJO，与 Spring 无关）。
 *
 * <p>既可以手写代码构造：</p>
 * <pre>{@code
 * CaptchaConfig config = new CaptchaConfig();
 * config.setWidth(320);
 * config.getClick().setCharset("en");
 * CaptchaEngine engine = new CaptchaEngine(config);
 * }</pre>
 *
 * <p>也可以在 Spring Boot 里直接由 {@code smart-captcha.*} 配置绑定：
 * starter 里的 {@code SmartCaptchaProperties} 继承本类，所以两种方式完全等价。</p>
 */
public class CaptchaConfig {

    /** 是否开启验证码。关闭后生成接口直接报错，拦截器放行。 */
    private boolean enabled = true;

    /** 验证码有效期（秒）。 */
    private int expireSeconds = 120;

    /** 同一张验证码允许的最大尝试次数。 */
    private int maxAttempts = 3;

    /** 验证通过后签发的 ticket 有效期（秒）。 */
    private int ticketExpireSeconds = 300;

    /** 允许的验证方式：slider / click / rotate。为空表示三种全部允许。 */
    private List<String> allowTypes = new ArrayList<>(List.of("slider", "click", "rotate"));

    /** 背景画布宽度（像素）。 */
    private int width = 360;

    /** 背景画布高度（像素）。 */
    private int height = 200;

    /** 最小作答耗时（毫秒），0 表示不校验。用于拦截脚本瞬时提交。 */
    private int minDurationMs = 0;

    private Slider slider = new Slider();
    private Click click = new Click();
    private Rotate rotate = new Rotate();
    private Background background = new Background();

    /** 画布宽度，带下限保护。 */
    public int canvasWidth() {
        return Math.max(200, width);
    }

    /** 画布高度，带下限保护。 */
    public int canvasHeight() {
        return Math.max(120, height);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(int expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getTicketExpireSeconds() {
        return ticketExpireSeconds;
    }

    public void setTicketExpireSeconds(int ticketExpireSeconds) {
        this.ticketExpireSeconds = ticketExpireSeconds;
    }

    public List<String> getAllowTypes() {
        return allowTypes;
    }

    public void setAllowTypes(List<String> allowTypes) {
        this.allowTypes = allowTypes;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getMinDurationMs() {
        return minDurationMs;
    }

    public void setMinDurationMs(int minDurationMs) {
        this.minDurationMs = minDurationMs;
    }

    public Slider getSlider() {
        return slider;
    }

    public void setSlider(Slider slider) {
        this.slider = slider;
    }

    public Click getClick() {
        return click;
    }

    public void setClick(Click click) {
        this.click = click;
    }

    public Rotate getRotate() {
        return rotate;
    }

    public void setRotate(Rotate rotate) {
        this.rotate = rotate;
    }

    public Background getBackground() {
        return background;
    }

    public void setBackground(Background background) {
        this.background = background;
    }

    /** 滑块验证参数。 */
    public static class Slider {
        /** 拼图块边长。 */
        private int pieceSize = 52;
        /** 横向容错（像素，原图坐标系）。 */
        private int tolerance = 8;

        public int getPieceSize() {
            return pieceSize;
        }

        public void setPieceSize(int pieceSize) {
            this.pieceSize = pieceSize;
        }

        public int getTolerance() {
            return tolerance;
        }

        public void setTolerance(int tolerance) {
            this.tolerance = tolerance;
        }
    }

    /** 文字点选参数。 */
    public static class Click {
        /** 需要点击的字符数量。 */
        private int wordCount = 3;
        /** 点击容错半径（像素）。 */
        private int tolerance = 22;
        /** 字号。 */
        private int fontSize = 30;
        /** 字符集：zh（中文，默认）/ en（英文）。单次生成可用 clickCharset 覆盖。 */
        private String charset = "zh";

        /**
         * 自定义字体对象（优先级最高，只能通过代码设置）。
         * 字号按 {@link #fontSize} 派生，样式保留传入值；
         * 字符集为中文但该字体渲染不了汉字时，回退到默认中文字体。
         */
        private transient Font font;

        /**
         * 字体文件路径（ttf / otf），支持绝对 / 相对路径（{@code ~} 自动展开）或
         * {@code classpath:} 资源，例如 {@code classpath:fonts/Xingkai.ttf}。
         * 同一文件按内容缓存，只加载一次；加载失败回退默认字体并打警告。
         */
        private String fontPath = "";

        /**
         * 自定义字体族名（可由 yaml 配置：{@code smart-captcha.click.font-family}）。
         * 系统未安装该字体时回退到默认字体选择逻辑。
         */
        private String fontFamily = "";

        public String getCharset() {
            return charset;
        }

        public void setCharset(String charset) {
            this.charset = charset;
        }

        public int getWordCount() {
            return wordCount;
        }

        public void setWordCount(int wordCount) {
            this.wordCount = wordCount;
        }

        public int getTolerance() {
            return tolerance;
        }

        public void setTolerance(int tolerance) {
            this.tolerance = tolerance;
        }

        public int getFontSize() {
            return fontSize;
        }

        public void setFontSize(int fontSize) {
            this.fontSize = fontSize;
        }

        public Font getFont() {
            return font;
        }

        public void setFont(Font font) {
            this.font = font;
        }

        public String getFontPath() {
            return fontPath;
        }

        public void setFontPath(String fontPath) {
            this.fontPath = fontPath == null ? "" : fontPath;
        }

        public String getFontFamily() {
            return fontFamily;
        }

        public void setFontFamily(String fontFamily) {
            this.fontFamily = fontFamily == null ? "" : fontFamily;
        }
    }

    /** 角度验证参数。 */
    public static class Rotate {
        /** 圆盘直径。 */
        private int size = 200;
        /** 角度容错（度）。 */
        private int tolerance = 8;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getTolerance() {
            return tolerance;
        }

        public void setTolerance(int tolerance) {
            this.tolerance = tolerance;
        }
    }

    /** 背景图来源配置。 */
    public static class Background {
        /**
         * 本地图片目录（兼容旧配置，等价于往 {@link #locations} 里加一条目录）。
         *
         * @deprecated 建议直接用 {@link #locations}，可以混合配置目录 / 文件 / URL
         */
        @Deprecated
        private String dir = "";

        /**
         * 背景图来源列表，每一条可以是：
         * <ul>
         *   <li>目录：{@code /data/captcha/bg}、{@code ~/Pictures/bg}（递归扫描 png/jpg/gif/bmp/webp）</li>
         *   <li>单个本地文件：{@code /data/captcha/bg/01.jpg}</li>
         *   <li>远程图片：{@code https://cdn.example.com/bg/02.jpg}</li>
         *   <li>classpath 资源：{@code classpath:captcha-bg/03.png}、{@code classpath:captcha-bg/*}（{@code **} 递归）</li>
         * </ul>
         */
        private List<String> locations = new ArrayList<>();

        /** 使用外部图片的概率（0~100），剩余概率使用程序化背景；默认 50。 */
        private int ratio = 50;

        /** 远程图片连接超时（毫秒）。 */
        private int connectTimeoutMs = 3000;

        /** 远程图片读取超时（毫秒）。 */
        private int readTimeoutMs = 5000;

        /** 远程图片缓存有效期（秒），0 表示只在启动时取一次、之后一直用缓存。 */
        private int refreshSeconds = 0;

        /** 合并 dir 与 locations 后的有效来源列表（去重、去空）。 */
        public List<String> resolvedLocations() {
            List<String> merged = new ArrayList<>();
            if (dir != null && !dir.isBlank()) {
                merged.add(dir.trim());
            }
            if (locations != null) {
                for (String location : locations) {
                    if (location != null && !location.isBlank()) {
                        merged.add(location.trim());
                    }
                }
            }
            return merged;
        }

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public List<String> getLocations() {
            return locations;
        }

        public void setLocations(List<String> locations) {
            this.locations = locations == null ? new ArrayList<>() : locations;
        }

        public int getRatio() {
            return ratio;
        }

        public void setRatio(int ratio) {
            this.ratio = ratio;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getRefreshSeconds() {
            return refreshSeconds;
        }

        public void setRefreshSeconds(int refreshSeconds) {
            this.refreshSeconds = refreshSeconds;
        }
    }
}
