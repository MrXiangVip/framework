import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Small code-drawn launcher asset; no external artwork is required. */
public final class GenerateIcon {
    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xf3313c)); g.fillRoundRect(0, 0, 128, 128, 29, 29);
            g.setColor(new Color(255, 255, 255, 45));
            for (int i = 0; i < 3; i++) g.drawLine(22, 35 + i * 28, 106, 35 + i * 28);
            g.setColor(Color.WHITE); g.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D line = new Path2D.Double(); line.moveTo(24, 88); line.lineTo(45, 67); line.lineTo(62, 77); line.lineTo(101, 37); g.draw(line);
            g.drawLine(81, 37, 102, 37); g.drawLine(102, 37, 102, 58);
        } finally { g.dispose(); }
        ImageIO.write(image, "png", new File(args[0]));
    }
}
