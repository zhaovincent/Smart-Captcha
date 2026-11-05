package cn.localtool.smartcaptcha.core.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;

/**
 * 工作量证明（PoW）—— 人机验证类型的成本门槛。
 *
 * <p>题目：找到一个 nonce，使得</p>
 * <pre>
 * SHA-256("SC1|" + token + "|" + salt + "|" + nonce)  的前 difficulty 位二进制为 0
 * </pre>
 *
 * <p>服务端只做一次哈希就能验算，客户端平均要做 {@code 2^difficulty} 次。
 * 这是"点一下就能通过"的验证码唯一真正能拦住批量脚本的手段：把每次尝试的
 * 成本从「发一个 HTTP 请求」抬高到「一段可控的 CPU 时间」。</p>
 *
 * <p>为什么 token 要进哈希：token 是本次挑战独有的，所以解出来的 nonce 无法
 * 跨会话复用，换一张验证码就得重算。salt 则保证同 token 前缀下题目也不重复。</p>
 *
 * <p>本类同时是给使用方（压测、服务端预计算、单元测试）的公开工具。</p>
 */
public final class ProofOfWork {

    /** 摘要算法标识，会原样下发给前端。 */
    public static final String ALGORITHM = "SHA-256";

    /** 领域分隔前缀，避免与其他系统的 PoW 共用同一哈希空间。 */
    public static final String DOMAIN = "SC1";

    /** 难度上下限（单位：前导 0 的二进制位）。上限防止配置写错把客户端卡死。 */
    public static final int MIN_DIFFICULTY = 6;
    /** 2^24 ≈ 1600 万次哈希，低端手机已经要几秒，再高就不适合放在登录链路上。 */
    public static final int MAX_DIFFICULTY = 24;

    private static final HexFormat HEX = HexFormat.of();

    private ProofOfWork() {
    }

    /** 生成随机盐（16 字节，32 位十六进制）。 */
    public static String salt(Random random) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    /** 客户端与服务端共用的哈希输入前缀。 */
    public static String prefix(String token, String salt) {
        return DOMAIN + "|" + token + "|" + salt + "|";
    }

    /** 计算摘要（null 安全，token / salt 为空串处理）。 */
    public static byte[] digest(String token, String salt, long nonce) {
        String input = prefix(nz(token), nz(salt)) + nonce;
        try {
            // MessageDigest 非线程安全，每次新建；单次校验的创建开销可以忽略
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 " + ALGORITHM, e);
        }
    }

    /** 摘要的十六进制形式（小写）。 */
    public static String digestHex(String token, String salt, long nonce) {
        return HEX.formatHex(digest(token, salt, nonce));
    }

    /** 摘要是否满足前 {@code difficulty} 位为 0。 */
    public static boolean satisfies(byte[] digest, int difficulty) {
        int bits = clampDifficulty(difficulty);
        if (bits == 0) {
            return true;
        }
        if (digest == null || digest.length * 8 < bits) {
            return false;
        }
        int fullBytes = bits >>> 3;
        for (int i = 0; i < fullBytes; i++) {
            if (digest[i] != 0) {
                return false;
            }
        }
        int restBits = bits & 7;
        if (restBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - restBits)) & 0xFF;
        return (digest[fullBytes] & mask) == 0;
    }

    /** 一步完成验算：nonce 是否让本题达标。 */
    public static boolean verify(String token, String salt, long nonce, int difficulty) {
        return satisfies(digest(token, salt, nonce), difficulty);
    }

    /**
     * 求解（主要给测试、压测与服务端内部使用）。
     *
     * @return 满足条件的 nonce；在 {@code maxNonce} 内找不到返回 {@code null}
     */
    public static Long solve(String token, String salt, int difficulty, long maxNonce) {
        long limit = Math.max(1, maxNonce);
        for (long nonce = 0; nonce < limit; nonce++) {
            if (verify(token, salt, nonce, difficulty)) {
                return nonce;
            }
        }
        return null;
    }

    /** 期望尝试次数的自然表述，用于日志与文档。 */
    public static long expectedAttempts(int difficulty) {
        return 1L << clampDifficulty(difficulty);
    }

    /** 难度规整到 [MIN_DIFFICULTY, MAX_DIFFICULTY]。 */
    public static int clampDifficulty(int difficulty) {
        if (difficulty <= 0) {
            return 0;
        }
        return Math.min(MAX_DIFFICULTY, Math.max(MIN_DIFFICULTY, difficulty));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
