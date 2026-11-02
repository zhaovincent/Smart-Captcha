package cn.localtool.smartcaptcha.spring.store;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 只有本机（或 -Dtest.redis.host / -Dtest.redis.port 指定）的 Redis 可连通时才执行测试类，
 * 保证没有 Redis 的环境里 {@code mvn test} 依然能跑通（这些用例自动跳过）。
 */
public class RedisAvailableCondition implements ExecutionCondition {

    static final String HOST = System.getProperty("test.redis.host",
            System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1"));

    static final String PORT = System.getProperty("test.redis.port",
            System.getenv().getOrDefault("REDIS_PORT", "6379"));

    public static boolean available() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, Integer.parseInt(PORT)), 500);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return available()
                ? ConditionEvaluationResult.enabled("Redis 可用：" + HOST + ":" + PORT)
                : ConditionEvaluationResult.disabled("Redis 不可用（" + HOST + ":" + PORT + "），跳过 Redis 存储测试");
    }
}
