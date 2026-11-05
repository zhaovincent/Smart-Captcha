package cn.localtool.smartcaptcha.core;

import cn.localtool.smartcaptcha.core.generator.CaptchaGenerator;
import cn.localtool.smartcaptcha.core.generator.ClickWordCaptchaGenerator;
import cn.localtool.smartcaptcha.core.generator.GenerateOptions;
import cn.localtool.smartcaptcha.core.generator.HumanCaptchaGenerator;
import cn.localtool.smartcaptcha.core.generator.RotateCaptchaGenerator;
import cn.localtool.smartcaptcha.core.generator.SliderCaptchaGenerator;
import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import cn.localtool.smartcaptcha.core.store.MemoryCaptchaStore;
import cn.localtool.smartcaptcha.core.support.BackgroundFactory;
import cn.localtool.smartcaptcha.core.support.ImageSupport;
import cn.localtool.smartcaptcha.core.support.ProofOfWork;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 验证码内核引擎：生成 + 校验 + ticket 签发/消费，纯 Java 实现，不依赖 Spring。
 *
 * <p>最小用法（内存存储、默认配置）：</p>
 * <pre>{@code
 * CaptchaEngine engine = new CaptchaEngine();
 * CaptchaChallenge challenge = engine.generate("slider", null, null);
 * VerifyResult result = engine.verify(VerifyInput.slider(challenge.id(), challenge.token(), 213.5));
 * }</pre>
 *
 * <p>自定义配置 / 存储 / 生成器：</p>
 * <pre>{@code
 * CaptchaEngine engine = CaptchaEngine.builder(config)
 *         .store(new MyCaptchaStore())        // 默认 MemoryCaptchaStore
 *         .generator(new MyGenerator())       // 追加/覆盖某种验证方式
 *         .build();
 * }</pre>
 *
 * <p>线程安全：引擎本身无可变状态，可全局单例使用；线程安全由 {@link CaptchaStore} 保证。</p>
 */
public class CaptchaEngine {

    private static final Logger log = Logger.getLogger(CaptchaEngine.class.getName());

    private final CaptchaConfig config;
    private final CaptchaStore store;
    private final BackgroundFactory backgrounds;
    private final Map<CaptchaType, CaptchaGenerator> generators;

    /** 默认配置 + 内存存储 + 程序化背景 + 内置三种验证方式。 */
    public CaptchaEngine() {
        this(new CaptchaConfig());
    }

    public CaptchaEngine(CaptchaConfig config) {
        this(config, null);
    }

    public CaptchaEngine(CaptchaConfig config, CaptchaStore store) {
        this(config, store, null, null, true);
    }

    public CaptchaEngine(CaptchaConfig config, CaptchaStore store,
                         BackgroundFactory backgrounds, List<CaptchaGenerator> generators) {
        this(config, store, backgrounds, generators, true);
    }

    private CaptchaEngine(CaptchaConfig config, CaptchaStore store, BackgroundFactory backgrounds,
                          List<CaptchaGenerator> generators, boolean loadPlugins) {
        this.config = config == null ? new CaptchaConfig() : config;
        this.store = store == null ? new MemoryCaptchaStore() : store;
        this.backgrounds = backgrounds == null
                ? new BackgroundFactory(this.config.getBackground())
                : backgrounds;

        Map<CaptchaType, CaptchaGenerator> map = new EnumMap<>(CaptchaType.class);
        // 1) 内置实现
        for (CaptchaGenerator builtin : List.of(
                new SliderCaptchaGenerator(), new ClickWordCaptchaGenerator(),
                new RotateCaptchaGenerator(), new HumanCaptchaGenerator())) {
            map.put(builtin.type(), builtin);
        }
        // 2) ServiceLoader 插件（覆盖同类型内置实现）
        if (loadPlugins) {
            loadPlugins(map);
        }
        // 3) 显式注册（优先级最高）
        if (generators != null) {
            for (CaptchaGenerator g : generators) {
                if (g != null && g.type() != null) {
                    map.put(g.type(), g);
                }
            }
        }
        this.generators = Map.copyOf(map);
    }

    /** 创建一个构建器，便于组合存储 / 生成器。 */
    public static Builder builder(CaptchaConfig config) {
        return new Builder(config);
    }

    // ------------------------------------------------------------------ 公开 API

    public CaptchaConfig config() {
        return config;
    }

    public CaptchaStore store() {
        return store;
    }

    /** 背景图工厂，可用于运行期重载图片源（例如目录里新增了图片）。 */
    public BackgroundFactory backgrounds() {
        return backgrounds;
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    /** 允许使用的验证方式（按配置顺序去重）。 */
    public List<CaptchaType> allowedTypes() {
        List<CaptchaType> pool = new ArrayList<>();
        for (String code : config.getAllowTypes()) {
            CaptchaType.of(code).ifPresent(t -> {
                if (!pool.contains(t)) {
                    pool.add(t);
                }
            });
        }
        return pool.isEmpty() ? List.of(CaptchaType.values()) : pool;
    }

    /** 已注册的生成器类型，便于排查“为什么某个类型不可用”。 */
    public List<CaptchaType> supportedTypes() {
        return List.copyOf(generators.keySet());
    }

    /**
     * 生成一张验证码。
     *
     * @param requestedType   slider / click / rotate；留空或 auto / random 表示从 {@link #allowedTypes()} 随机
     * @param backgroundImage 外部背景图（dataURL 或纯 base64），为空则由服务端生成
     * @param clickCharset    文字点选字符集覆盖（zh / en），可为空
     */
    public CaptchaChallenge generate(String requestedType, String backgroundImage, String clickCharset) {
        if (!config.isEnabled()) {
            throw new CaptchaException(4001, "验证码功能已关闭");
        }
        List<CaptchaType> pool = allowedTypes();
        CaptchaType type = resolveType(requestedType, pool);

        int width = config.canvasWidth();
        int height = config.canvasHeight();

        CaptchaGenerator generator = generators.get(type);
        if (generator == null) {
            throw new CaptchaException(4001, "暂不支持的验证方式：" + type.getCode());
        }
        // 人机验证不出图，跳过背景生成与 PNG 编码，省掉一次无意义的 CPU 开销
        BufferedImage background = generator.requiresBackground()
                ? resolveBackground(backgroundImage, width, height)
                : null;
        GenerationResult result = generator.generate(background, config,
                ThreadLocalRandom.current(), GenerateOptions.of(clickCharset));

        String id = UUID.randomUUID().toString().replace("-", "");
        String token = UUID.randomUUID().toString().replace("-", "") + Long.toHexString(System.nanoTime());
        long expireAt = System.currentTimeMillis() + config.getExpireSeconds() * 1000L;
        store.save(new CaptchaSession(id, token, type, result.answer(), expireAt));

        return new CaptchaChallenge(id, token, type.getCode(), result.width(), result.height(),
                result.image() == null ? null : ImageSupport.toDataUrl(result.image()),
                result.slider(), result.click(), result.rotate(), result.human(),
                config.getExpireSeconds(), result.tip());
    }

    /** 生成一张验证码（随机方式 + 服务端背景）。 */
    public CaptchaChallenge generate() {
        return generate(null, null, null);
    }

    /** 生成指定方式的验证码。 */
    public CaptchaChallenge generate(String type) {
        return generate(type, null, null);
    }

    /**
     * 校验用户行为。通过时返回一次性 ticket，并立即销毁该验证码。
     *
     * @throws CaptchaException id 不存在 / token 不匹配 / 次数耗尽后再次提交
     */
    public VerifyResult verify(VerifyInput input) {
        if (input == null || input.id() == null || input.id().isBlank()) {
            throw new CaptchaException(4001, "缺少验证码 id");
        }
        CaptchaSession session = store.find(input.id())
                .orElseThrow(() -> new CaptchaException(4002, "验证码不存在或已过期，请刷新"));

        if (input.token() == null || !session.getToken().equals(input.token())) {
            throw new CaptchaException(4002, "验证码凭证无效，请刷新");
        }
        if (input.type() != null && !input.type().isBlank()
                && !session.getType().getCode().equalsIgnoreCase(input.type())) {
            throw new CaptchaException(4001, "验证方式与验证码不匹配");
        }

        if (config.getMinDurationMs() > 0 && input.duration() != null
                && input.duration() < config.getMinDurationMs()) {
            return reject(session, "操作过快，请重试");
        }

        boolean passed;
        String rejectMessage = null;
        switch (session.getType()) {
            case SLIDER -> passed = checkSlider(session.getAnswer(), input);
            case CLICK -> passed = checkClick(session.getAnswer(), input);
            case ROTATE -> passed = checkRotate(session.getAnswer(), input);
            case HUMAN -> {
                rejectMessage = humanFailure(session, input);
                passed = rejectMessage == null;
            }
            default -> passed = false;
        }

        if (passed) {
            store.remove(session.getId());
            String ticket = store.issueTicket(config.getTicketExpireSeconds());
            log.fine(() -> "captcha passed, type=" + session.getType().getCode());
            return VerifyResult.pass(ticket);
        }
        return reject(session, rejectMessage);
    }

    /** 业务侧消费 ticket（一次性）。 */
    public boolean consumeTicket(String ticket) {
        return store.consumeTicket(ticket);
    }

    /** 给前端读取的公开配置（不含任何答案或密钥）。 */
    public Map<String, Object> publicConfig() {
        List<String> types = allowedTypes().stream().map(CaptchaType::getCode).toList();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", this.config.isEnabled());
        config.put("types", types);
        config.put("width", this.config.getWidth());
        config.put("height", this.config.getHeight());
        config.put("expireIn", this.config.getExpireSeconds());
        config.put("maxAttempts", this.config.getMaxAttempts());
        config.put("sliderTolerance", this.config.getSlider().getTolerance());
        config.put("clickTolerance", this.config.getClick().getTolerance());
        config.put("rotateTolerance", this.config.getRotate().getTolerance());
        config.put("clickWordCount", this.config.getClick().getWordCount());
        config.put("clickCharset", this.config.getClick().getCharset());
        config.put("rotateSize", this.config.getRotate().getSize());
        config.put("humanDifficulty", ProofOfWork.clampDifficulty(this.config.getHuman().getDifficulty()));
        config.put("humanDelayMinMs", this.config.getHuman().getDelayMinMs());
        config.put("humanDelayMaxMs", this.config.getHuman().getDelayMaxMs());
        config.put("humanMinMoves", this.config.getHuman().getMinMoves());
        config.put("store", store.type());
        config.put("background", backgrounds.describe());
        config.put("backgroundImages", backgrounds.externalImageCount());
        return config;
    }

    // ------------------------------------------------------------------ 内部方法

    private void loadPlugins(Map<CaptchaType, CaptchaGenerator> map) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = CaptchaEngine.class.getClassLoader();
        }
        try {
            for (CaptchaGenerator plugin : ServiceLoader.load(CaptchaGenerator.class, loader)) {
                if (plugin.type() != null) {
                    map.put(plugin.type(), plugin);
                    log.info("已通过 ServiceLoader 注册验证码生成器：" + plugin.getClass().getName());
                }
            }
        } catch (ServiceConfigurationError | RuntimeException e) {
            log.log(Level.WARNING, "加载 CaptchaGenerator 扩展失败，已忽略", e);
        }
    }

    private CaptchaType resolveType(String requested, List<CaptchaType> pool) {
        if (requested == null || requested.isBlank()
                || "auto".equalsIgnoreCase(requested) || "random".equalsIgnoreCase(requested)) {
            return CaptchaType.randomOf(pool);
        }
        Optional<CaptchaType> type = CaptchaType.of(requested);
        if (type.isEmpty()) {
            throw new CaptchaException(4001, "未知的验证方式：" + requested);
        }
        if (!pool.contains(type.get())) {
            throw new CaptchaException(4001, "当前配置不支持该验证方式：" + requested);
        }
        return type.get();
    }

    /** 外部传入的背景图（dataURL / base64）优先，否则由服务端自动生成。 */
    private BufferedImage resolveBackground(String backgroundImage, int width, int height) {
        if (backgroundImage != null && !backgroundImage.isBlank()) {
            BufferedImage external = ImageSupport.readBase64(backgroundImage);
            if (external != null) {
                return ImageSupport.cover(external, width, height);
            }
            log.warning("外部背景图解析失败，改用服务端自动生成背景");
        }
        return backgrounds.next(width, height, ThreadLocalRandom.current());
    }

    private VerifyResult reject(CaptchaSession session, String message) {
        int attempts = store.increaseAttempts(session.getId());
        int left = Math.max(0, config.getMaxAttempts() - attempts);
        if (left <= 0) {
            store.remove(session.getId());
            return VerifyResult.fail(message != null ? message : "验证失败，请重新获取验证码", 0);
        }
        return VerifyResult.fail(message != null ? message : "验证失败，还剩 " + left + " 次机会", left);
    }

    private boolean checkSlider(CaptchaAnswer answer, VerifyInput input) {
        if (input.x() == null || answer.targetX() == null) {
            return false;
        }
        return Math.abs(input.x() - answer.targetX()) <= config.getSlider().getTolerance();
    }

    private boolean checkClick(CaptchaAnswer answer, VerifyInput input) {
        List<Coordinate> points = input.points();
        List<double[]> expect = answer.points();
        if (points == null || expect == null || points.size() != expect.size()) {
            return false;
        }
        double tolerance = config.getClick().getTolerance();
        for (int i = 0; i < expect.size(); i++) {
            Coordinate p = points.get(i);
            if (p == null) {
                return false;
            }
            double[] target = expect.get(i);
            if (Math.hypot(p.x() - target[0], p.y() - target[1]) > tolerance) {
                return false;
            }
        }
        return true;
    }

    private boolean checkRotate(CaptchaAnswer answer, VerifyInput input) {
        if (input.angle() == null || answer.targetAngle() == null) {
            return false;
        }
        return circularDiff(input.angle(), answer.targetAngle()) <= config.getRotate().getTolerance();
    }

    /** 两个角度之间的最小差值（0~180）。 */
    private double circularDiff(double a, double b) {
        double diff = Math.abs((a - b) % 360);
        return diff > 180 ? 360 - diff : diff;
    }

    /**
     * 人机验证判定。返回 null 表示通过，否则返回给用户看的具体原因。
     *
     * <p>三道关，顺序即成本从高到低：</p>
     * <ol>
     *     <li>工作量证明：客户端必须提交一个让摘要前 N 位为 0 的 nonce，
     *         且哈希里带本次 token —— 换题就不能复用，批量刷题要付 CPU；</li>
     *     <li>最短耗时：服务端签发到提交的时间差，脚本再快也快不过这条线；</li>
     *     <li>交互痕迹：指针 / 触摸 / 键盘至少发生过一次真实事件。</li>
     * </ol>
     */
    private String humanFailure(CaptchaSession session, VerifyInput input) {
        CaptchaAnswer.Human expected = session.getAnswer() == null ? null : session.getAnswer().human();
        if (expected == null) {
            return "验证数据异常，请刷新";
        }
        if (expected.difficulty() > 0) {
            if (input.nonce() == null) {
                return "缺少工作量证明";
            }
            if (!ProofOfWork.verify(session.getToken(), expected.salt(), input.nonce(), expected.difficulty())) {
                return "工作量证明无效";
            }
        }
        if (expected.minDurationMs() > 0) {
            // 人机验证靠服务端计时兜底，所以没带耗时本身就算异常 —— 否则少传一个字段就能跳过这一关
            if (input.duration() == null) {
                return "缺少作答耗时";
            }
            // 关卡一：客户端上报的耗时（从拿到题目到提交）
            if (input.duration() < expected.minDurationMs()) {
                return "操作过快，请重试";
            }
            // 关卡二：服务端自己计的「签发 → 收到校验请求」耗时。
            // 客户端上报值可以随便谎报，但这道线是服务器时钟量出来的：
            // 脚本拿到题立刻提交，无论上报多少毫秒都过不去。
            long issuedAt = session.getExpireAt() - config.getExpireSeconds() * 1000L;
            long serverElapsed = System.currentTimeMillis() - issuedAt;
            if (serverElapsed < expected.minDurationMs()) {
                return "操作过快，请重试";
            }
        }
        if (expected.requireTrace() && !hasInteraction(input.trace(), expected.minMoves())) {
            return "未检测到有效的人机操作";
        }
        return null;
    }

    /**
     * 是否存在真实交互痕迹。
     *
     * <p>这里刻意放宽：鼠标已经停在按钮上、用户直接点下去是完全正常的用法（此时
     * 一次移动事件都没有）。所以「移动够多」或「有键盘输入」或「有指针按下」任一成立即通过。
     * 它挡不住会跑浏览器的事件模拟，但能把「纯 HTTP 请求」这一类拦在门外，真正的门槛仍是 PoW。</p>
     */
    private boolean hasInteraction(HumanTrace trace, int minMoves) {
        if (trace == null) {
            return false;
        }
        if (trace.clicks() >= 1 || trace.keys() >= 1) {
            return true;
        }
        return trace.moves() >= Math.max(1, minMoves);
    }

    /** 引擎构建器。 */
    public static final class Builder {

        private final CaptchaConfig config;
        private CaptchaStore store;
        private BackgroundFactory backgrounds;
        private final List<CaptchaGenerator> generators = new ArrayList<>();
        private boolean loadPlugins = true;

        private Builder(CaptchaConfig config) {
            this.config = config == null ? new CaptchaConfig() : config;
        }

        /** 会话仓库，默认 {@link MemoryCaptchaStore}。 */
        public Builder store(CaptchaStore store) {
            this.store = store;
            return this;
        }

        /** 自定义背景工厂（例如外部传入图片目录）。 */
        public Builder backgroundFactory(BackgroundFactory backgrounds) {
            this.backgrounds = backgrounds;
            return this;
        }

        /** 追加一个生成器，同类型会覆盖内置实现。 */
        public Builder generator(CaptchaGenerator generator) {
            if (generator != null) {
                this.generators.add(generator);
            }
            return this;
        }

        /** 是否通过 {@link ServiceLoader} 加载 {@code META-INF/services} 中的扩展，默认开启。 */
        public Builder serviceLoader(boolean enabled) {
            this.loadPlugins = enabled;
            return this;
        }

        public CaptchaEngine build() {
            return new CaptchaEngine(config, store, backgrounds, generators, loadPlugins);
        }
    }
}
