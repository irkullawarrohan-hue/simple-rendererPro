package renderer;
public class PostProcessing {

    private final int width;
    private final int height;
    private double[] hdrBuffer;
    private double[] brightnessBuffer;
    private double[] blurBuffer1;
    private double[] blurBuffer2;
    private double[] ssaoBuffer;
    private boolean ssaoEnabled = true;
    private boolean bloomEnabled = true;
    private boolean vignetteEnabled = true;

    private double ssaoRadius = 0.5;
    private double ssaoIntensity = 1.0;
    private int ssaoSamples = 16;

    private double bloomThreshold = 0.8;
    private double bloomIntensity = 0.3;
    private int bloomIterations = 5;

    private double vignetteIntensity = 0.3;
    private double vignetteSoftness = 0.5;

    private double exposure = 1.0;
    private double gamma = 2.2;

    private Vector3[] ssaoKernel;
    private double[] ssaoNoise;

    public PostProcessing(int width, int height) {
        this.width = width;
        this.height = height;

        int size = width * height;
        hdrBuffer = new double[size * 3];
        brightnessBuffer = new double[size];
        blurBuffer1 = new double[size];
        blurBuffer2 = new double[size];
        ssaoBuffer = new double[size];

        generateSSAOKernel();
        generateSSAONoise();
    }
    private void generateSSAOKernel() {
        ssaoKernel = new Vector3[ssaoSamples];

        for (int i = 0; i < ssaoSamples; i++) {
            double x = Math.random() * 2.0 - 1.0;
            double y = Math.random() * 2.0 - 1.0;
            double z = Math.random();

            Vector3 sample = new Vector3(x, y, z).normalize();
            double scale = (double) i / ssaoSamples;
            scale = lerp(0.1, 1.0, scale * scale);
            sample = sample.scale(scale);

            ssaoKernel[i] = sample;
        }
    }
    private void generateSSAONoise() {
        ssaoNoise = new double[16 * 2];
        for (int i = 0; i < 16; i++) {
            ssaoNoise[i * 2] = Math.random() * 2.0 - 1.0;
            ssaoNoise[i * 2 + 1] = Math.random() * 2.0 - 1.0;
        }
    }
    public void process(int[] frameBuffer, double[] depthBuffer, GBuffer gBuffer) {
        convertToHDR(frameBuffer);
        if (ssaoEnabled && gBuffer != null) {
            computeSSAO(depthBuffer, gBuffer);
            applySSAO();
        }
        if (bloomEnabled) {
            extractBrightAreas();
            applyBloom();
        }
        if (vignetteEnabled) {
            applyVignette();
        }
        toneMapAndConvert(frameBuffer);
    }
    private void convertToHDR(int[] frameBuffer) {
        for (int i = 0; i < width * height; i++) {
            int argb = frameBuffer[i];
            double r = ((argb >> 16) & 0xFF) / 255.0;
            double g = ((argb >> 8) & 0xFF) / 255.0;
            double b = (argb & 0xFF) / 255.0;
            hdrBuffer[i * 3] = srgbToLinear(r);
            hdrBuffer[i * 3 + 1] = srgbToLinear(g);
            hdrBuffer[i * 3 + 2] = srgbToLinear(b);
        }
    }
    private void computeSSAO(double[] depthBuffer, GBuffer gBuffer) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;

                if (!gBuffer.hasData(x, y)) {
                    ssaoBuffer[idx] = 1.0;
                    continue;
                }

                double depth = depthBuffer[idx];
                Vector3 normal = gBuffer.getNormal(x, y);

                if (normal == null || depth <= 0 || Double.isInfinite(depth)) {
                    ssaoBuffer[idx] = 1.0;
                    continue;
                }
                int noiseIdx = ((y % 4) * 4 + (x % 4)) * 2;
                double noiseX = ssaoNoise[noiseIdx];
                double noiseY = ssaoNoise[noiseIdx + 1];
                Vector3 tangent = new Vector3(noiseX, noiseY, 0).normalize();
                tangent = tangent.sub(normal.scale(tangent.dot(normal))).normalize();
                Vector3 bitangent = normal.cross(tangent);

                double occlusion = 0.0;
                int validSamples = 0;

                for (int s = 0; s < ssaoSamples; s++) {
                    Vector3 sample = ssaoKernel[s];
                    Vector3 samplePos = tangent.scale(sample.x)
                            .add(bitangent.scale(sample.y))
                            .add(normal.scale(sample.z));

                    double sampleRadius = ssaoRadius * (1.0 / Math.max(0.1, depth));
                    samplePos = samplePos.scale(sampleRadius);

                    int sampleX = x + (int)(samplePos.x * width * 0.5);
                    int sampleY = y + (int)(samplePos.y * height * 0.5);
                    sampleX = Math.max(0, Math.min(width - 1, sampleX));
                    sampleY = Math.max(0, Math.min(height - 1, sampleY));

                    int sampleIdx = sampleY * width + sampleX;
                    double sampleDepth = depthBuffer[sampleIdx];

                    if (sampleDepth <= 0 || Double.isInfinite(sampleDepth)) continue;

                    double rangeCheck = smoothstep(0.0, 1.0,
                            ssaoRadius / Math.abs(depth - sampleDepth + 0.001));
                    if (sampleDepth < depth - 0.01) {
                        occlusion += rangeCheck;
                    }
                    validSamples++;
                }

                if (validSamples > 0) {
                    occlusion = 1.0 - (occlusion / validSamples) * ssaoIntensity;
                } else {
                    occlusion = 1.0;
                }

                ssaoBuffer[idx] = Math.max(0.0, Math.min(1.0, occlusion));
            }
        }
        blurSSAO();
    }
    private void blurSSAO() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sum = 0;
                int count = 0;
                for (int dx = -2; dx <= 2; dx++) {
                    int sx = x + dx;
                    if (sx >= 0 && sx < width) {
                        sum += ssaoBuffer[y * width + sx];
                        count++;
                    }
                }
                blurBuffer1[y * width + x] = sum / count;
            }
        }

        // Vertical pass
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sum = 0;
                int count = 0;
                for (int dy = -2; dy <= 2; dy++) {
                    int sy = y + dy;
                    if (sy >= 0 && sy < height) {
                        sum += blurBuffer1[sy * width + x];
                        count++;
                    }
                }
                ssaoBuffer[y * width + x] = sum / count;
            }
        }
    }
    private void applySSAO() {
        for (int i = 0; i < width * height; i++) {
            double ao = ssaoBuffer[i];
            hdrBuffer[i * 3] *= ao;
            hdrBuffer[i * 3 + 1] *= ao;
            hdrBuffer[i * 3 + 2] *= ao;
        }
    }
    private void extractBrightAreas() {
        for (int i = 0; i < width * height; i++) {
            double r = hdrBuffer[i * 3];
            double g = hdrBuffer[i * 3 + 1];
            double b = hdrBuffer[i * 3 + 2];
            double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            double brightness = Math.max(0, luminance - bloomThreshold);
            brightness = brightness / (brightness + 1.0); // Soft knee

            brightnessBuffer[i] = brightness;
        }
    }
    private void applyBloom() {
        System.arraycopy(brightnessBuffer, 0, blurBuffer1, 0, width * height);
        for (int iteration = 0; iteration < bloomIterations; iteration++) {
            int radius = 1 << iteration;
            gaussianBlurHorizontal(blurBuffer1, blurBuffer2, radius);
            gaussianBlurVertical(blurBuffer2, blurBuffer1, radius);
        }
        for (int i = 0; i < width * height; i++) {
            double bloom = blurBuffer1[i] * bloomIntensity;
            hdrBuffer[i * 3] += bloom;
            hdrBuffer[i * 3 + 1] += bloom;
            hdrBuffer[i * 3 + 2] += bloom;
        }
    }
    private void gaussianBlurHorizontal(double[] src, double[] dst, int radius) {
        double[] kernel = generateGaussianKernel(radius);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sum = 0;
                double weightSum = 0;

                for (int i = -radius; i <= radius; i++) {
                    int sx = x + i;
                    if (sx >= 0 && sx < width) {
                        double weight = kernel[i + radius];
                        sum += src[y * width + sx] * weight;
                        weightSum += weight;
                    }
                }

                dst[y * width + x] = sum / weightSum;
            }
        }
    }
    private void gaussianBlurVertical(double[] src, double[] dst, int radius) {
        double[] kernel = generateGaussianKernel(radius);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sum = 0;
                double weightSum = 0;

                for (int i = -radius; i <= radius; i++) {
                    int sy = y + i;
                    if (sy >= 0 && sy < height) {
                        double weight = kernel[i + radius];
                        sum += src[sy * width + x] * weight;
                        weightSum += weight;
                    }
                }

                dst[y * width + x] = sum / weightSum;
            }
        }
    }
    private double[] generateGaussianKernel(int radius) {
        double[] kernel = new double[radius * 2 + 1];
        double sigma = radius / 2.0;
        double sum = 0;

        for (int i = -radius; i <= radius; i++) {
            double value = Math.exp(-(i * i) / (2 * sigma * sigma));
            kernel[i + radius] = value;
            sum += value;
        }

        // Normalize
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }

        return kernel;
    }
    private void applyVignette() {
        double cx = width / 2.0;
        double cy = height / 2.0;
        double maxDist = Math.sqrt(cx * cx + cy * cy);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;

                // Calculate distance from center (normalized)
                double dx = (x - cx) / cx;
                double dy = (y - cy) / cy;
                double dist = Math.sqrt(dx * dx + dy * dy);

                // Vignette falloff
                double vignette = 1.0 - smoothstep(vignetteSoftness, 1.0, dist) * vignetteIntensity;

                hdrBuffer[idx * 3] *= vignette;
                hdrBuffer[idx * 3 + 1] *= vignette;
                hdrBuffer[idx * 3 + 2] *= vignette;
            }
        }
    }
    private void toneMapAndConvert(int[] frameBuffer) {
        for (int i = 0; i < width * height; i++) {
            double r = hdrBuffer[i * 3] * exposure;
            double g = hdrBuffer[i * 3 + 1] * exposure;
            double b = hdrBuffer[i * 3 + 2] * exposure;

            r = acesToneMap(r);
            g = acesToneMap(g);
            b = acesToneMap(b);

            r = Math.pow(r, 1.0 / gamma);
            g = Math.pow(g, 1.0 / gamma);
            b = Math.pow(b, 1.0 / gamma);

            int ri = (int)(Math.max(0, Math.min(1, r)) * 255);
            int gi = (int)(Math.max(0, Math.min(1, g)) * 255);
            int bi = (int)(Math.max(0, Math.min(1, b)) * 255);

            frameBuffer[i] = 0xFF000000 | (ri << 16) | (gi << 8) | bi;
        }
    }
    private double acesToneMap(double x) {
        double a = 2.51;
        double b = 0.03;
        double c = 2.43;
        double d = 0.59;
        double e = 0.14;
        return Math.max(0, (x * (a * x + b)) / (x * (c * x + d) + e));
    }

    private double srgbToLinear(double x) {
        return x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    // Getters/Setters

    public void setSSAOEnabled(boolean enabled) { this.ssaoEnabled = enabled; }
    public boolean isSSAOEnabled() { return ssaoEnabled; }

    public void setSSAORadius(double radius) { this.ssaoRadius = Math.max(0.1, Math.min(2.0, radius)); }
    public double getSSAORadius() { return ssaoRadius; }

    public void setSSAOIntensity(double intensity) { this.ssaoIntensity = Math.max(0, Math.min(3.0, intensity)); }
    public double getSSAOIntensity() { return ssaoIntensity; }

    public void setBloomEnabled(boolean enabled) { this.bloomEnabled = enabled; }
    public boolean isBloomEnabled() { return bloomEnabled; }

    public void setBloomThreshold(double threshold) { this.bloomThreshold = Math.max(0, Math.min(2.0, threshold)); }
    public double getBloomThreshold() { return bloomThreshold; }

    public void setBloomIntensity(double intensity) { this.bloomIntensity = Math.max(0, Math.min(2.0, intensity)); }
    public double getBloomIntensity() { return bloomIntensity; }

    public void setVignetteEnabled(boolean enabled) { this.vignetteEnabled = enabled; }
    public boolean isVignetteEnabled() { return vignetteEnabled; }

    public void setVignetteIntensity(double intensity) { this.vignetteIntensity = Math.max(0, Math.min(1.0, intensity)); }
    public double getVignetteIntensity() { return vignetteIntensity; }

    public void setExposure(double exposure) { this.exposure = Math.max(0.1, Math.min(5.0, exposure)); }
    public double getExposure() { return exposure; }

    public void setGamma(double gamma) { this.gamma = Math.max(1.0, Math.min(3.0, gamma)); }
    public double getGamma() { return gamma; }
}

