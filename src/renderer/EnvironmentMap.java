package renderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class EnvironmentMap {

    private BufferedImage image;
    private int width;
    private int height;
    private boolean loaded = false;
    private double[][] linearR;
    private double[][] linearG;
    private double[][] linearB;
    private double[][] irradianceR;
    private double[][] irradianceG;
    private double[][] irradianceB;
    private int irradianceSize = 32;

    public EnvironmentMap() {
    }

    public EnvironmentMap(String path) {
        load(path);
    }

    public boolean load(String path) {
        try {
            image = ImageIO.read(new File(path));
            if (image == null) {
                System.err.println("EnvironmentMap: Failed to load " + path);
                return false;
            }

            width = image.getWidth();
            height = image.getHeight();
            convertToLinear();
            computeIrradianceMap();

            loaded = true;
            System.out.println("EnvironmentMap: Loaded " + path + " (" + width + "x" + height + ")");
            return true;

        } catch (Exception e) {
            System.err.println("EnvironmentMap: Error loading " + path + ": " + e.getMessage());
            return false;
        }
    }
    private void convertToLinear() {
        linearR = new double[height][width];
        linearG = new double[height][width];
        linearB = new double[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // sRGB to linear conversion
                linearR[y][x] = srgbToLinear(r / 255.0);
                linearG[y][x] = srgbToLinear(g / 255.0);
                linearB[y][x] = srgbToLinear(b / 255.0);
            }
        }
    }
    private void computeIrradianceMap() {
        irradianceR = new double[irradianceSize][irradianceSize * 2];
        irradianceG = new double[irradianceSize][irradianceSize * 2];
        irradianceB = new double[irradianceSize][irradianceSize * 2];
        for (int y = 0; y < irradianceSize; y++) {
            for (int x = 0; x < irradianceSize * 2; x++) {
                // Convert pixel to direction
                double u = (x + 0.5) / (irradianceSize * 2);
                double v = (y + 0.5) / irradianceSize;
                Vector3 normal = uvToDirection(u, v);

                // Integrate hemisphere around this normal
                double[] irradiance = computeIrradianceForNormal(normal);
                irradianceR[y][x] = irradiance[0];
                irradianceG[y][x] = irradiance[1];
                irradianceB[y][x] = irradiance[2];
            }
        }
    }

    private double[] computeIrradianceForNormal(Vector3 normal) {
        double irradianceR = 0, irradianceG = 0, irradianceB = 0;
        Vector3 up = Math.abs(normal.y) < 0.999 ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0);
        Vector3 tangent = normal.cross(up).normalize();
        Vector3 bitangent = normal.cross(tangent);
        int numSamples = 64;
        int sqrtSamples = 8;

        for (int i = 0; i < sqrtSamples; i++) {
            for (int j = 0; j < sqrtSamples; j++) {
                double u1 = (i + 0.5) / sqrtSamples;
                double u2 = (j + 0.5) / sqrtSamples;

                double phi = 2.0 * Math.PI * u1;
                double cosTheta = Math.sqrt(1.0 - u2);
                double sinTheta = Math.sqrt(u2);

                double x = Math.cos(phi) * sinTheta;
                double y = Math.sin(phi) * sinTheta;
                double z = cosTheta;

                Vector3 sampleDir = tangent.scale(x).add(bitangent.scale(y)).add(normal.scale(z));
                double[] sample = sampleLinear(sampleDir);

                irradianceR += sample[0];
                irradianceG += sample[1];
                irradianceB += sample[2];
            }
        }


        double invSamples = Math.PI / numSamples;
        return new double[] {
            irradianceR * invSamples,
            irradianceG * invSamples,
            irradianceB * invSamples
        };
    }
    public double[] sampleSpecular(Vector3 direction) {
        if (!loaded) {
            return new double[] { 0.1, 0.1, 0.1 }; // Default gray
        }
        return sampleLinear(direction);
    }

    public double[] sampleSpecularRough(Vector3 direction, double roughness) {
        if (!loaded) {
            return new double[] { 0.1, 0.1, 0.1 };
        }
        if (roughness < 0.1) {
            return sampleLinear(direction);
        }

        int numSamples = (int)(4 + roughness * 12);
        double coneAngle = roughness * roughness * Math.PI * 0.5;

        Vector3 up = Math.abs(direction.y) < 0.999 ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0);
        Vector3 tangent = direction.cross(up).normalize();
        Vector3 bitangent = direction.cross(tangent);

        double r = 0, g = 0, b = 0;

        for (int i = 0; i < numSamples; i++) {
            double u1 = (i + 0.5) / numSamples;
            double u2 = ((i * 7) % numSamples + 0.5) / numSamples;

            double phi = 2.0 * Math.PI * u1;
            double cosTheta = 1.0 - u2 * (1.0 - Math.cos(coneAngle));
            double sinTheta = Math.sqrt(1.0 - cosTheta * cosTheta);

            double x = Math.cos(phi) * sinTheta;
            double y = Math.sin(phi) * sinTheta;
            double z = cosTheta;

            Vector3 sampleDir = tangent.scale(x).add(bitangent.scale(y)).add(direction.scale(z)).normalize();
            double[] sample = sampleLinear(sampleDir);

            r += sample[0];
            g += sample[1];
            b += sample[2];
        }

        double inv = 1.0 / numSamples;
        return new double[] { r * inv, g * inv, b * inv };
    }
    public double[] sampleDiffuse(Vector3 normal) {
        if (!loaded) {
            return new double[] { 0.1, 0.1, 0.1 };
        }
        double[] uv = directionToUV(normal);
        double u = uv[0] * (irradianceSize * 2 - 1);
        double v = uv[1] * (irradianceSize - 1);

        int x0 = (int) u;
        int y0 = (int) v;
        int x1 = (x0 + 1) % (irradianceSize * 2);
        int y1 = Math.min(y0 + 1, irradianceSize - 1);

        double fx = u - x0;
        double fy = v - y0;

        double r = bilinear(irradianceR[y0][x0], irradianceR[y0][x1],
                           irradianceR[y1][x0], irradianceR[y1][x1], fx, fy);
        double g = bilinear(irradianceG[y0][x0], irradianceG[y0][x1],
                           irradianceG[y1][x0], irradianceG[y1][x1], fx, fy);
        double b = bilinear(irradianceB[y0][x0], irradianceB[y0][x1],
                           irradianceB[y1][x0], irradianceB[y1][x1], fx, fy);

        return new double[] { r, g, b };
    }

    private double[] sampleLinear(Vector3 direction) {
        double[] uv = directionToUV(direction);

        // Bilinear sampling
        double u = uv[0] * (width - 1);
        double v = uv[1] * (height - 1);

        int x0 = (int) u;
        int y0 = (int) v;
        int x1 = (x0 + 1) % width;
        int y1 = Math.min(y0 + 1, height - 1);

        double fx = u - x0;
        double fy = v - y0;

        double r = bilinear(linearR[y0][x0], linearR[y0][x1], linearR[y1][x0], linearR[y1][x1], fx, fy);
        double g = bilinear(linearG[y0][x0], linearG[y0][x1], linearG[y1][x0], linearG[y1][x1], fx, fy);
        double b = bilinear(linearB[y0][x0], linearB[y0][x1], linearB[y1][x0], linearB[y1][x1], fx, fy);

        return new double[] { r, g, b };
    }
    private double[] directionToUV(Vector3 dir) {
        double phi = Math.atan2(dir.x, dir.z);     // Longitude
        double theta = Math.asin(Math.max(-1, Math.min(1, dir.y))); // Latitude

        double u = (phi + Math.PI) / (2.0 * Math.PI);
        double v = (theta + Math.PI / 2.0) / Math.PI;
        v = 1.0 - v;

        return new double[] { u, v };
    }

    private Vector3 uvToDirection(double u, double v) {
        v = 1.0 - v;

        double phi = u * 2.0 * Math.PI - Math.PI;
        double theta = v * Math.PI - Math.PI / 2.0;

        double cosTheta = Math.cos(theta);
        double x = Math.sin(phi) * cosTheta;
        double y = Math.sin(theta);
        double z = Math.cos(phi) * cosTheta;

        return new Vector3(x, y, z);
    }

    private double bilinear(double v00, double v10, double v01, double v11, double fx, double fy) {
        double v0 = v00 * (1 - fx) + v10 * fx;
        double v1 = v01 * (1 - fx) + v11 * fx;
        return v0 * (1 - fy) + v1 * fy;
    }

    private double srgbToLinear(double srgb) {
        if (srgb <= 0.04045) {
            return srgb / 12.92;
        } else {
            return Math.pow((srgb + 0.055) / 1.055, 2.4);
        }
    }

    // Getters
    public boolean isLoaded() { return loaded; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}

