package cn.localtool.smartcaptcha.core.store;

import cn.localtool.smartcaptcha.core.CaptchaSession;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 内存版验证码仓库（内核默认实现，纯 JDK）。
 *
 * <p>单机可用、零外部依赖；多实例部署时各节点的验证码互不可见，
 * 需要共享时请换用 Redis / Redisson 实现（starter 模块提供）。</p>
 *
 * <p>后台线程每 {@code sweepSeconds} 秒清理一次过期会话与 ticket；
 * 用完请调用 {@link #close()}（Spring 环境下由容器负责，Bean 声明
 * {@code destroyMethod = "close"} 即可）。</p>
 */
public class MemoryCaptchaStore implements CaptchaStore, AutoCloseable {

    private static final Logger log = Logger.getLogger(MemoryCaptchaStore.class.getName());

    private static final int DEFAULT_SWEEP_SECONDS = 30;

    private final Map<String, CaptchaSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> tickets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper;

    public MemoryCaptchaStore() {
        this(DEFAULT_SWEEP_SECONDS);
    }

    public MemoryCaptchaStore(int sweepSeconds) {
        long interval = Math.max(1, sweepSeconds);
        this.sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "captcha-store-sweeper");
            t.setDaemon(true);
            return t;
        });
        this.sweeper.scheduleAtFixedRate(this::evictExpired, interval, interval, TimeUnit.SECONDS);
    }

    @Override
    public void save(CaptchaSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    public Optional<CaptchaSession> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        CaptchaSession session = sessions.get(id);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expired()) {
            sessions.remove(id);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public synchronized int increaseAttempts(String id) {
        CaptchaSession session = sessions.get(id);
        if (session == null) {
            return 0;
        }
        session.setAttempts(session.getAttempts() + 1);
        return session.getAttempts();
    }

    @Override
    public void remove(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    @Override
    public String issueTicket(int ttlSeconds) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, System.currentTimeMillis() + Math.max(1, ttlSeconds) * 1000L);
        return ticket;
    }

    @Override
    public boolean consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return false;
        }
        Long expireAt = tickets.remove(ticket);
        return expireAt != null && expireAt > System.currentTimeMillis();
    }

    @Override
    public int sessionCount() {
        return sessions.size();
    }

    @Override
    public String type() {
        return "memory";
    }

    /** 停止后台清理线程。 */
    @Override
    public void close() {
        sweeper.shutdownNow();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> e.getValue().getExpireAt() < now);
        tickets.entrySet().removeIf(e -> e.getValue() < now);
        log.fine(() -> "captcha store swept, sessions=" + sessions.size() + ", tickets=" + tickets.size());
    }
}
