package cn.localtool.smartcaptcha.core.generator;

import cn.localtool.smartcaptcha.core.CaptchaAnswer;
import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaConfig;
import cn.localtool.smartcaptcha.core.CaptchaType;
import cn.localtool.smartcaptcha.core.GenerationResult;
import cn.localtool.smartcaptcha.core.support.ImageSupport;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 角度旋转验证码：
 * 圆形图下方有一个缺口，整图被随机旋转了 theta 度，用户把缺口转回正上方即可通过。
 *
 * <p>生成时对图像做逆时针旋转 theta，因此正确答案就是“顺时针旋转 theta”，
 * 前端用 CSS rotate(angle)（正值为顺时针）即可直接对齐。</p>
 */
public class RotateCaptchaGenerator implements CaptchaGenerator {

    private static final double NOTCH_HALF_DEGREE = 13.0;

    @Override
    public CaptchaType type() {
        return CaptchaType.ROTATE;
    }

    @Override
    public GenerationResult generate(BufferedImage background, CaptchaConfig config, Random random) {
        int size = Math.min(config.getRotate().getSize(),
                Math.min(background.getWidth(), background.getHeight()));
        size = Math.max(120, size);

        BufferedImage square = ImageSupport.cover(background, size, size);
        double cx = size / 2.0;
        double cy = size / 2.0;
        double radius = size / 2.0 - 2;

        // 1. 圆形底图（先在正上方挖缺口）
        BufferedImage disc = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D dg = disc.createGraphics();
        ImageSupport.applyQualityHints(dg);
        dg.setClip(new Ellipse2D.Double(1, 1, size - 2, size - 2));
        dg.drawImage(square, 0, 0, null);
        dg.setClip(null);
        // 边缘微光 + 内圈
        dg.setPaint(new RadialGradientPaint(new Point2D.Float((float) cx, (float) cy), (float) radius,
                new float[]{0.55f, 0.92f, 1f},
                new Color[]{new Color(255, 255, 255, 0), new Color(255, 255, 255, 0),
                        new Color(8, 15, 30, 90)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        dg.fill(new Ellipse2D.Double(1, 1, size - 2, size - 2));
        dg.setColor(new Color(255, 255, 255, 150));
        dg.setStroke(new BasicStroke(2f));
        dg.draw(new Ellipse2D.Double(1.5, 1.5, size - 3, size - 3));

        cutNotch(disc, cx, cy, radius);
        drawNotchEdge(dg, cx, cy, radius);
        dg.dispose();

        // 2. 整图随机旋转（逆时针），缺口随之离开正上方；范围 30°~140°，方向随机
        double magnitude = 30 + random.nextDouble() * 110;
        double theta = random.nextBoolean() ? magnitude : -magnitude;
        BufferedImage rotated = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D rg = rotated.createGraphics();
        ImageSupport.applyQualityHints(rg);
        rg.drawImage(disc, AffineTransform.getRotateInstance(-Math.toRadians(theta), cx, cy), null);
        rg.dispose();

        return GenerationResult.of(rotated, CaptchaAnswer.rotate(theta),
                new CaptchaChallenge.RotateChallenge(size),
                "拖动圆盘，让缺口对准上方的标记");
    }

    private void cutNotch(BufferedImage image, double cx, double cy, double radius) {
        double start = radius * 0.55;
        double half = Math.toRadians(NOTCH_HALF_DEGREE);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                double dx = x - cx;
                double dy = y - cy;
                double dist = Math.hypot(dx, dy);
                if (dist < start || dist > radius) {
                    continue;
                }
                // 0 表示正上方，顺时针为正
                double angle = Math.atan2(dx, -dy);
                if (Math.abs(angle) < half) {
                    image.setRGB(x, y, 0);
                }
            }
        }
    }

    /** 给缺口描一条青色边缘，便于用户识别。 */
    private void drawNotchEdge(Graphics2D g, double cx, double cy, double radius) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(103, 232, 249, 210));

        double inner = radius * 0.55;
        double outer = radius - 0.5;
        g.draw(new Arc2D.Double(cx - outer, cy - outer, outer * 2, outer * 2,
                90 + NOTCH_HALF_DEGREE, -NOTCH_HALF_DEGREE * 2, Arc2D.OPEN));
        g.draw(new Arc2D.Double(cx - inner, cy - inner, inner * 2, inner * 2,
                90 + NOTCH_HALF_DEGREE, -NOTCH_HALF_DEGREE * 2, Arc2D.OPEN));

        double rad = Math.toRadians(NOTCH_HALF_DEGREE);
        double dx = Math.sin(rad);
        double dy = Math.cos(rad);
        g.drawLine((int) (cx + dx * inner), (int) (cy - dy * inner),
                (int) (cx + dx * outer), (int) (cy - dy * outer));
        g.drawLine((int) (cx - dx * inner), (int) (cy - dy * inner),
                (int) (cx - dx * outer), (int) (cy - dy * outer));
    }
}
