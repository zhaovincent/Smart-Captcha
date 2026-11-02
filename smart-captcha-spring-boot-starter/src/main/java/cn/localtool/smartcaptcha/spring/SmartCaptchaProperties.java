package cn.localtool.smartcaptcha.spring;

import cn.localtool.smartcaptcha.core.CaptchaConfig;
import cn.localtool.smartcaptcha.spring.store.StoreTypes;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code smart-captcha.*} 配置项。
 *
 * <p>直接继承内核的 {@link CaptchaConfig}，所以配置文件绑定出来的对象可以原样交给
 * {@code CaptchaEngine} 使用，不存在两套配置字段。</p>
 */
@ConfigurationProperties(prefix = "smart-captcha")
public class SmartCaptchaProperties extends CaptchaConfig {

    /** 跨域允许的来源（仅作用于 /api/**），留空表示不注册 CORS 规则。 */
    private List<String> allowedOrigins = new ArrayList<>(List.of("*"));

    /** 验证码会话 / ticket 存储。 */
    private Store store = new Store();

    /** 业务接口拦截器。 */
    private Interceptor interceptor = new Interceptor();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Interceptor getInterceptor() {
        return interceptor;
    }

    public void setInterceptor(Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    /**
     * {@code smart-captcha.interceptor.*}：是否把 {@code CaptchaInterceptor} 注册到 Spring MVC。
     *
     * <p>默认<b>不开启</b>。不开启时框架不会拦截任何接口，业务接口自己接收前端传来的
     * ticket 并调用 {@code CaptchaEngine#consumeTicket(String)} 校验即可；开启后，标注了
     * {@code @RequireCaptcha} 的接口会在进入方法前统一校验并消费 ticket，失败抛
     * {@code CaptchaTicketException}。</p>
     */
    public static class Interceptor {

        /** 是否开启拦截器，默认 false（由业务接口自行校验 ticket）。 */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** 验证码存储（会话 / ticket）配置。 */
    public static class Store {

        /** memory（默认）/ redis（RedisTemplate）/ redisson。 */
        private String type = StoreTypes.MEMORY;

        /** Redis key 前缀，多套环境共用一个库时用于隔离。 */
        private String keyPrefix = "smart-captcha";

        /** Redisson 单机地址，可省略协议头，例如 127.0.0.1:6379。 */
        private String address = "redis://127.0.0.1:6379";

        /** Redisson 密码，留空表示无密码。 */
        private String password = "";

        /** Redisson 数据库编号。 */
        private int database = 0;

        /** 归一化后的类型：memory / redis / redisson，未知值按 memory 处理。 */
        public String normalizedType() {
            return StoreTypes.normalize(type);
        }

        /** 配置值是否属于已知类型（留空视为默认 memory）。 */
        public boolean isKnownType() {
            return StoreTypes.isKnown(type);
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }
    }
}
