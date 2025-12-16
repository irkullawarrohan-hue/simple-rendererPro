package renderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class Texture {
    private final BufferedImage img;
    private final int w, h;

    public Texture(String path) throws Exception {
        img = ImageIO.read(new File(path));
        w = img.getWidth(); h = img.getHeight();
    }
    public int sampleRGB(double u, double v) {
        double uu = u - Math.floor(u);
        double vv = v - Math.floor(v);
        vv = 1.0 - vv;
        int x = (int)(uu * w) % w;
        int y = (int)(vv * h) % h;
        if (x < 0) x += w;
        if (y < 0) y += h;
        return img.getRGB(x, y);
    }
    public int getWidth() { return w; }
    public int getHeight() { return h; }
}
