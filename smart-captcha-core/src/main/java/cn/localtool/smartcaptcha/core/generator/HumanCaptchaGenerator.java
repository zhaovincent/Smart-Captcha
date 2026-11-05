package cn.localtool.smartcaptcha.core.generator;

import cn.localtool.smartcaptcha.core.CaptchaAnswer;
import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaConfig;
import cn.localtool.smartcaptcha.core.CaptchaType;
import cn.localtool.smartcaptcha.core.GenerationResult;
import cn.localtool.smartcaptcha.core.support.ProofOfWork;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 一键人机验证：用户只需点一下按钮。
 *
 * <p>它不画图、也不问用户问题，全部工作量放在两处：</p>
 * <ul>
 *     <li>下发一个 PoW 题目（salt + 难度），让客户端先算一阵；</li>
 *     <li>把「耗时 + 行为证据」一起带回来，由服务端判定。</li>
 * </ul>
 *
 * <p>因为没有图像，{@link #requiresBackground()} 返回 false，引擎不会浪费 CPU
 * 去生成背景图 —— 这也是它比另外三种更轻的原因。</p>
 */
public class HumanCaptchaGenerator implements CaptchaGenerator {

    @Override
    public CaptchaType type() {
        return CaptchaType.HUMAN;
    }

    /** 人机验证不需要背景图。 */
    @Override
    public boolean requiresBackground() {
        return false;
    }

    @Override
    public GenerationResult generate(BufferedImage background, CaptchaConfig config, Random random) {
        CaptchaConfig.Human cfg = config.getHuman();
        String salt = ProofOfWork.salt(random);
        int difficulty = ProofOfWork.clampDifficulty(cfg.getDifficulty());
        int minMoves = Math.max(0, cfg.getMinMoves());
        // 每张验证码随机抽取「要求等待时长」，写进答案快照 —— 服务端按本次抽取值判定，
        // 客户端拿到的 challenge 里也是同一个值（用于决定点击后等多久再调校验接口）
        int requiredDelay = randomDelay(cfg, random);

        CaptchaAnswer answer = CaptchaAnswer.human(new CaptchaAnswer.Human(
                salt, difficulty, requiredDelay, minMoves, cfg.isRequireTrace()));

        CaptchaChallenge.HumanChallenge human = new CaptchaChallenge.HumanChallenge(
                ProofOfWork.ALGORITHM, ProofOfWork.DOMAIN, salt, difficulty, requiredDelay, minMoves,
                cfg.isRequireTrace());

        return GenerationResult.human(answer, human, config.canvasWidth(), config.canvasHeight(),
                "点击按钮完成人机验证");
    }

    /** 在 [delayMinMs, delayMaxMs] 内均匀取值；区间非法时回退到较小值。 */
    private int randomDelay(CaptchaConfig.Human cfg, Random random) {
        int min = Math.max(0, cfg.getDelayMinMs());
        int max = Math.max(min, cfg.getDelayMaxMs());
        return min + (max > min ? random.nextInt(max - min + 1) : 0);
    }
}
