package cn.localtool.smartcaptcha.spring.store;

import java.util.Locale;
import java.util.Set;

/**
 * 存储类型的归一化：允许 {@code redis-template} / {@code lettuce} 之类的别名，
 * 未知值一律按 {@code memory} 处理（并打警告），避免启动失败。
 */
public final class StoreTypes {

    public static final String MEMORY = "memory";
    public static final String REDIS = "redis";
    public static final String REDISSON = "redisson";

    private static final Set<String> KNOWN = Set.of(
            "memory", "local", "in-memory", "redis", "redis-template", "redistemplate",
            "lettuce", "spring-data-redis", "redisson");

    private StoreTypes() {
    }

    public static String normalize(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "redis", "redis-template", "redistemplate", "lettuce", "spring-data-redis" -> REDIS;
            case "redisson" -> REDISSON;
            default -> MEMORY;
        };
    }

    public static boolean isKnown(String type) {
        return type == null || type.isBlank() || KNOWN.contains(type.trim().toLowerCase(Locale.ROOT));
    }
}
