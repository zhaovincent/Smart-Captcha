package cn.localtool.smartcaptcha.core.generator;

import cn.localtool.smartcaptcha.core.CaptchaAnswer;
import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaConfig;
import cn.localtool.smartcaptcha.core.CaptchaType;
import cn.localtool.smartcaptcha.core.GenerationResult;
import cn.localtool.smartcaptcha.core.support.ImageSupport;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 滑块拼图验证码：
 * 背景挖出拼图缺口，同时输出带透明通道的拼图块；用户拖动拼图块对齐缺口。
 */
public class SliderCaptchaGenerator implements CaptchaGenerator {

    @Override
    public CaptchaType type() {
        return CaptchaType.SLIDER;
    }

    @Override
    public GenerationResult generate(BufferedImage background, CaptchaConfig config, Random random) {
        int width = background.getWidth();
        int height = background.getHeight();
        int size = Math.min(config.getSlider().getPieceSize(), Math.min(width / 2, height / 2));
        int tab = Math.max(6, Math.round(size * 0.18f));
        int pieceWidth = size + tab;
        int pieceHeight = size;

        int minX = Math.max(6, (int) (width * 0.42));
        int maxX = Math.max(minX, width - pieceWidth - 6);
        int targetX = minX + random.nextInt(maxX - minX + 1);

        int minY = Math.max(6, (int) (height * 0.12));
        int maxY = Math.max(minY, height - pieceHeight - Math.max(6, (int) (height * 0.10)));
        int targetY = minY + random.nextInt(maxY - minY + 1);

        Area shape = pieceShape(size, tab);

        // 1. 抠出拼图块（保留原背景纹理 + 白色描边 + 投影）
        BufferedImage piece = new BufferedImage(pieceWidth + 4, pieceHeight + 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = piece.createGraphics();
        ImageSupport.applyQualityHints(pg);
        pg.translate(2, 2);
        pg.setClip(shape);
        pg.drawImage(background, -targetX, -targetY, null);
        pg.setClip(null);
        pg.setComposite(AlphaComposite.SrcOver);
        pg.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        pg.setColor(new Color(255, 255, 255, 235));
        pg.draw(shape);
        pg.setColor(new Color(120, 220, 255, 160));
        pg.setStroke(new BasicStroke(0.9f));
        pg.draw(shape);
        pg.dispose();

        // 2. 在背景上挖出缺口
        BufferedImage canvas = ImageSupport.copy(background);
        Graphics2D g = canvas.createGraphics();
        ImageSupport.applyQualityHints(g);
        Area hole = new Area(shape);
        hole.transform(java.awt.geom.AffineTransform.getTranslateInstance(targetX, targetY));

        g.setColor(new Color(8, 12, 24, 135));
        g.fill(hole);
        g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 70));
        g.draw(hole);
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(new Color(255, 255, 255, 225));
        g.draw(hole);
        g.dispose();

        CaptchaChallenge.SliderChallenge slider = new CaptchaChallenge.SliderChallenge(
                ImageSupport.toDataUrl(piece), piece.getWidth(), piece.getHeight(), targetY - 2);

        return GenerationResult.of(canvas, CaptchaAnswer.slider(targetX - 2, piece.getWidth()),
                slider, "拖动滑块，把拼图块对准缺口");
    }

    /** 圆角方块 + 右侧半圆凸起的拼图形状。 */
    private Area pieceShape(int size, int tab) {
        double arc = size * 0.18;
        Area area = new Area(new RoundRectangle2D.Double(0, 0, size, size, arc, arc));
        double knobX = size;
        double knobY = size / 2.0;
        area.add(new Area(new Ellipse2D.Double(knobX - tab, knobY - tab, tab * 2, tab * 2)));
        return area;
    }
}
