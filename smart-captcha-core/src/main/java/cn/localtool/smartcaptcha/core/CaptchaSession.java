package cn.localtool.smartcaptcha.core;

/**
 * 一次验证码会话（带过期时间与失败次数）。
 */
public class CaptchaSession {

    private final String id;
    private final String token;
    private final CaptchaType type;
    private final CaptchaAnswer answer;
    private final long expireAt;
    private int attempts;

    public CaptchaSession(String id, String token, CaptchaType type, CaptchaAnswer answer, long expireAt) {
        this.id = id;
        this.token = token;
        this.type = type;
        this.answer = answer;
        this.expireAt = expireAt;
    }

    public String getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public CaptchaType getType() {
        return type;
    }

    public CaptchaAnswer getAnswer() {
        return answer;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public boolean expired() {
        return System.currentTimeMillis() > expireAt;
    }
}
