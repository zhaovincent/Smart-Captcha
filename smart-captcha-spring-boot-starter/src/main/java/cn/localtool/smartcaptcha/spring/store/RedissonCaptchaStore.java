package cn.localtool.smartcaptcha.spring.store;

import cn.localtool.smartcaptcha.core.CaptchaAnswer;
import cn.localtool.smartcaptcha.core.CaptchaSession;
import cn.localtool.smartcaptcha.core.CaptchaType;
import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 Redisson 客户端的验证码仓库，适用于多实例部署。
 *
 * <p>统一使用 {@link StringCodec}，数据在 Redis 里是明文，方便排查，也与
 * {@link RedisCaptchaStore} 的 key 布局保持一致：</p>
 * <ul>
 *     <li>{@code {prefix}:session:{id}} —— RMap(hash)：token / type / answer(JSON) / expireAt；</li>
 *     <li>{@code {prefix}:attempts:{id}} —— RAtomicLong，失败次数原子自增（与验证码同 TTL）；</li>
 *     <li>{@code {prefix}:ticket:{ticket}} —— RBucket，消费用 GETDEL 原子取值并删除。</li>
 * </ul>
 */
public class RedissonCaptchaStore implements CaptchaStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedissonCaptchaStore.class);

    private static final String F_TOKEN = "token";
    private static final String F_TYPE = "type";
    private static final String F_ANSWER = "answer";
    private static final String F_EXPIRE_AT = "expireAt";

    private final RedissonClient client;
    private final ObjectMapper objectMapper;
    private final String prefix;
    private final boolean closeClient;

    public RedissonCaptchaStore(RedissonClient client, ObjectMapper objectMapper, String keyPrefix) {
        this(client, objectMapper, keyPrefix, false);
    }

    /**
     * @param closeClient 是否在 {@link #close()} 时关闭 Redisson 客户端。
     *                    客户端由本类创建时为 true；复用业务已有客户端时应为 false。
     */
    public RedissonCaptchaStore(RedissonClient client, ObjectMapper objectMapper,
                                String keyPrefix, boolean closeClient) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.prefix = keyPrefix;
        this.closeClient = closeClient;
    }

    @Override
    public void save(CaptchaSession session) {
        RMap<String, String> map = sessionMap(session.getId());
        Map<String, String> body = new LinkedHashMap<>();
        body.put(F_TOKEN, session.getToken());
        body.put(F_TYPE, session.getType().getCode());
        body.put(F_ANSWER, writeAnswer(session.getAnswer()));
        body.put(F_EXPIRE_AT, Long.toString(session.getExpireAt()));
        // 覆盖写：先清掉可能残留的旧字段
        map.delete();
        map.putAll(body);
        map.expire(Duration.ofSeconds(ttlSeconds(session.getExpireAt())));
        client.getAtomicLong(attemptsKey(session.getId())).delete();
    }

    @Override
    public Optional<CaptchaSession> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        RMap<String, String> map = sessionMap(id);
        if (!map.isExists()) {
            return Optional.empty();
        }
        Map<String, String> body = map.readAllMap();
        if (body.isEmpty()) {
            map.delete();
            return Optional.empty();
        }
        CaptchaSession session = readSession(id, body);
        if (session == null || session.expired()) {
            remove(id);
            return Optional.empty();
        }
        RAtomicLong counter = client.getAtomicLong(attemptsKey(id));
        session.setAttempts(counter.isExists() ? (int) counter.get() : 0);
        return Optional.of(session);
    }

    @Override
    public int increaseAttempts(String id) {
        if (id == null || id.isBlank()) {
            return 0;
        }
        RMap<String, String> map = sessionMap(id);
        if (!map.isExists()) {
            return 0;
        }
        String expireAtRaw = map.get(F_EXPIRE_AT);
        RAtomicLong counter = client.getAtomicLong(attemptsKey(id));
        long attempts = counter.incrementAndGet();
        if (expireAtRaw != null) {
            counter.expire(Duration.ofSeconds(ttlSeconds(Long.parseLong(expireAtRaw))));
        }
        return (int) attempts;
    }

    @Override
    public void remove(String id) {
        if (id != null && !id.isBlank()) {
            sessionMap(id).delete();
            client.getAtomicLong(attemptsKey(id)).delete();
        }
    }

    @Override
    public String issueTicket(int ttlSeconds) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        RBucket<String> bucket = client.getBucket(ticketKey(ticket), StringCodec.INSTANCE);
        bucket.set("1", Duration.ofSeconds(Math.max(1, ttlSeconds)));
        return ticket;
    }

    @Override
    public boolean consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return false;
        }
        // getAndDelete = GETDEL，原子取值并删除，防重放
        return client.<String>getBucket(ticketKey(ticket), StringCodec.INSTANCE).getAndDelete() != null;
    }

    @Override
    public int sessionCount() {
        int count = 0;
        for (String ignored : client.getKeys().getKeysByPattern(prefix + ":session:*")) {
            count++;
        }
        return count;
    }

    @Override
    public String type() {
        return "redisson";
    }

    @Override
    public void close() {
        if (!closeClient) {
            return;
        }
        try {
            client.shutdown();
        } catch (RuntimeException e) {
            log.warn("关闭 Redisson 客户端异常", e);
        }
    }

    private RMap<String, String> sessionMap(String id) {
        return client.getMap(sessionKey(id), StringCodec.INSTANCE);
    }

    private CaptchaSession readSession(String id, Map<String, String> body) {
        try {
            String token = body.get(F_TOKEN);
            String typeCode = body.get(F_TYPE);
            String answerJson = body.get(F_ANSWER);
            String expireAtRaw = body.get(F_EXPIRE_AT);
            if (token == null || typeCode == null || answerJson == null || expireAtRaw == null) {
                return null;
            }
            Optional<CaptchaType> type = CaptchaType.of(typeCode);
            if (type.isEmpty()) {
                return null;
            }
            CaptchaAnswer answer = objectMapper.readValue(answerJson, CaptchaAnswer.class);
            return new CaptchaSession(id, token, type.get(), answer, Long.parseLong(expireAtRaw));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("验证码会话反序列化失败，已丢弃：id={}", id, e);
            return null;
        }
    }

    private String writeAnswer(CaptchaAnswer answer) {
        try {
            return objectMapper.writeValueAsString(answer);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("验证码答案序列化失败", e);
        }
    }

    private String sessionKey(String id) {
        return prefix + ":session:" + id;
    }

    private String attemptsKey(String id) {
        return prefix + ":attempts:" + id;
    }

    private String ticketKey(String ticket) {
        return prefix + ":ticket:" + ticket;
    }

    private static long ttlSeconds(long expireAt) {
        long millis = expireAt - System.currentTimeMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}
