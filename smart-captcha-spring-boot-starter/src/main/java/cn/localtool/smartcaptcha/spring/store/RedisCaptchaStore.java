package cn.localtool.smartcaptcha.spring.store;

import cn.localtool.smartcaptcha.core.CaptchaAnswer;
import cn.localtool.smartcaptcha.core.CaptchaSession;
import cn.localtool.smartcaptcha.core.CaptchaType;
import cn.localtool.smartcaptcha.core.store.CaptchaStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 基于 Spring Data Redis（{@code StringRedisTemplate}）的验证码仓库，适用于多实例部署。
 *
 * <p>存储结构：</p>
 * <ul>
 *     <li>{@code {prefix}:session:{id}} —— hash：token / type / answer(JSON) / expireAt；</li>
 *     <li>{@code {prefix}:attempts:{id}} —— string 计数，失败次数用 INCR 原子累加；</li>
 *     <li>{@code {prefix}:ticket:{ticket}} —— string，值为 1，消费用 GETDEL 原子删除。</li>
 * </ul>
 * 三种 key 都带 TTL，过期由 Redis 自动回收。
 */
public class RedisCaptchaStore implements CaptchaStore {

    private static final Logger log = LoggerFactory.getLogger(RedisCaptchaStore.class);

    private static final String F_TOKEN = "token";
    private static final String F_TYPE = "type";
    private static final String F_ANSWER = "answer";
    private static final String F_EXPIRE_AT = "expireAt";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String prefix;

    public RedisCaptchaStore(StringRedisTemplate redis, ObjectMapper objectMapper, String keyPrefix) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.prefix = keyPrefix;
    }

    @Override
    public void save(CaptchaSession session) {
        String key = sessionKey(session.getId());
        Map<String, String> body = new LinkedHashMap<>();
        body.put(F_TOKEN, session.getToken());
        body.put(F_TYPE, session.getType().getCode());
        body.put(F_ANSWER, writeAnswer(session.getAnswer()));
        body.put(F_EXPIRE_AT, Long.toString(session.getExpireAt()));
        redis.opsForHash().putAll(key, body);
        redis.expire(key, Duration.ofSeconds(ttlSeconds(session.getExpireAt())));
        redis.delete(attemptsKey(session.getId()));
    }

    @Override
    public Optional<CaptchaSession> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String key = sessionKey(id);
        Map<Object, Object> body = redis.opsForHash().entries(key);
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }
        CaptchaSession session = readSession(id, body);
        if (session == null) {
            remove(id);
            return Optional.empty();
        }
        if (session.expired()) {
            remove(id);
            return Optional.empty();
        }
        String attempts = redis.opsForValue().get(attemptsKey(id));
        session.setAttempts(attempts == null ? 0 : Integer.parseInt(attempts));
        return Optional.of(session);
    }

    @Override
    public int increaseAttempts(String id) {
        if (id == null || id.isBlank()) {
            return 0;
        }
        String key = sessionKey(id);
        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            return 0;
        }
        Object expireAt = redis.opsForHash().get(key, F_EXPIRE_AT);
        Long attempts = redis.opsForValue().increment(attemptsKey(id));
        if (expireAt != null) {
            // 计数 key 与验证码同 TTL：反复失败也不会让它提前过期或永久驻留
            redis.expire(attemptsKey(id), Duration.ofSeconds(ttlSeconds(Long.parseLong(expireAt.toString()))));
        }
        return attempts == null ? 0 : attempts.intValue();
    }

    @Override
    public void remove(String id) {
        if (id != null && !id.isBlank()) {
            redis.delete(List.of(sessionKey(id), attemptsKey(id)));
        }
    }

    @Override
    public String issueTicket(int ttlSeconds) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(ticketKey(ticket), "1", Duration.ofSeconds(Math.max(1, ttlSeconds)));
        return ticket;
    }

    @Override
    public boolean consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return false;
        }
        // GETDEL：取值与删除是原子的，天然防重放
        return redis.opsForValue().getAndDelete(ticketKey(ticket)) != null;
    }

    @Override
    public int sessionCount() {
        Set<String> keys = redis.keys(prefix + ":session:*");
        return keys == null ? 0 : keys.size();
    }

    @Override
    public String type() {
        return "redis";
    }

    private CaptchaSession readSession(String id, Map<Object, Object> body) {
        try {
            String token = stringValue(body, F_TOKEN);
            String typeCode = stringValue(body, F_TYPE);
            String answerJson = stringValue(body, F_ANSWER);
            String expireAtRaw = stringValue(body, F_EXPIRE_AT);
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

    private static String stringValue(Map<Object, Object> body, String field) {
        Object value = body.get(field);
        return value == null ? null : value.toString();
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

    /** 剩余秒数，至少 1 秒，避免写入即过期。 */
    private static long ttlSeconds(long expireAt) {
        long millis = expireAt - System.currentTimeMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}
