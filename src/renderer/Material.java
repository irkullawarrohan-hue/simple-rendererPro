package renderer;
public class Material {
    private String name;
    private Vector3 albedoColor;

    private double metalness;
    private double roughness;
    private Vector3 emissionColor;
    private double emissionIntensity;
    private double aoIntensity;
    private double specularF0;
    private double normalIntensity;
    private double ior;
    private Texture albedoTexture;
    private Texture normalTexture;
    private Texture roughnessTexture;
    private Texture metalnessTexture;
    private Texture aoTexture;
    private Texture emissionTexture;
    private boolean doubleSided;
    public enum AlphaMode { OPAQUE, MASK, BLEND }
    private AlphaMode alphaMode;
    private double alphaCutoff;
    private boolean unlit;

    //Constructor

    public Material() {
        this("Unnamed Material");
    }

    public Material(String name) {
        this.name = name;
        this.albedoColor = new Vector3(0.8, 0.8, 0.8);
        this.metalness = 0.0;
        this.roughness = 0.5;
        this.emissionColor = new Vector3(0, 0, 0);
        this.emissionIntensity = 0.0;
        this.aoIntensity = 1.0;
        this.specularF0 = 0.04;
        this.normalIntensity = 1.0;
        this.ior = 1.5;
        this.doubleSided = false;
        this.alphaMode = AlphaMode.OPAQUE;
        this.alphaCutoff = 0.5;
        this.unlit = false;
    }
    public static Material preset(MaterialPreset preset) {
        Material mat = new Material(preset.name());

        switch (preset) {
            case GOLD:
                mat.albedoColor = new Vector3(1.0, 0.766, 0.336); // Gold color
                mat.metalness = 1.0;
                mat.roughness = 0.3;
                break;

            case SILVER:
                mat.albedoColor = new Vector3(0.972, 0.960, 0.915);
                mat.metalness = 1.0;
                mat.roughness = 0.2;
                break;

            case COPPER:
                mat.albedoColor = new Vector3(0.955, 0.637, 0.538);
                mat.metalness = 1.0;
                mat.roughness = 0.35;
                break;

            case IRON:
                mat.albedoColor = new Vector3(0.56, 0.57, 0.58);
                mat.metalness = 1.0;
                mat.roughness = 0.5;
                break;

            case ALUMINUM:
                mat.albedoColor = new Vector3(0.913, 0.921, 0.925);
                mat.metalness = 1.0;
                mat.roughness = 0.25;
                break;

            case CHROME:
                mat.albedoColor = new Vector3(0.549, 0.556, 0.554);
                mat.metalness = 1.0;
                mat.roughness = 0.05;
                break;

            case PLASTIC_RED:
                mat.albedoColor = new Vector3(0.8, 0.1, 0.1);
                mat.metalness = 0.0;
                mat.roughness = 0.4;
                break;

            case PLASTIC_WHITE:
                mat.albedoColor = new Vector3(0.95, 0.95, 0.95);
                mat.metalness = 0.0;
                mat.roughness = 0.35;
                break;

            case PLASTIC_BLACK:
                mat.albedoColor = new Vector3(0.02, 0.02, 0.02);
                mat.metalness = 0.0;
                mat.roughness = 0.4;
                break;

            case RUBBER:
                mat.albedoColor = new Vector3(0.1, 0.1, 0.1);
                mat.metalness = 0.0;
                mat.roughness = 0.9;
                break;

            case WOOD:
                mat.albedoColor = new Vector3(0.6, 0.4, 0.2);
                mat.metalness = 0.0;
                mat.roughness = 0.7;
                break;

            case MARBLE:
                mat.albedoColor = new Vector3(0.95, 0.93, 0.88);
                mat.metalness = 0.0;
                mat.roughness = 0.2;
                break;

            case CONCRETE:
                mat.albedoColor = new Vector3(0.5, 0.5, 0.5);
                mat.metalness = 0.0;
                mat.roughness = 0.85;
                break;

            case GLASS:
                mat.albedoColor = new Vector3(0.95, 0.95, 0.95);
                mat.metalness = 0.0;
                mat.roughness = 0.0;
                mat.ior = 1.52;
                mat.specularF0 = 0.04;
                break;

            case WATER:
                mat.albedoColor = new Vector3(0.3, 0.5, 0.7);
                mat.metalness = 0.0;
                mat.roughness = 0.0;
                mat.ior = 1.33;
                break;

            case SKIN:
                mat.albedoColor = new Vector3(0.8, 0.6, 0.5);
                mat.metalness = 0.0;
                mat.roughness = 0.6;
                break;

            case FABRIC:
                mat.albedoColor = new Vector3(0.5, 0.5, 0.6);
                mat.metalness = 0.0;
                mat.roughness = 0.95;
                break;

            case EMISSIVE_WHITE:
                mat.albedoColor = new Vector3(1, 1, 1);
                mat.emissionColor = new Vector3(1, 1, 1);
                mat.emissionIntensity = 5.0;
                mat.metalness = 0.0;
                mat.roughness = 1.0;
                break;

            case EMISSIVE_BLUE:
                mat.albedoColor = new Vector3(0.1, 0.3, 1.0);
                mat.emissionColor = new Vector3(0.2, 0.5, 1.0);
                mat.emissionIntensity = 3.0;
                mat.metalness = 0.0;
                mat.roughness = 0.8;
                break;

            case CLAY:
            default:
                mat.albedoColor = new Vector3(0.6, 0.6, 0.6);
                mat.metalness = 0.0;
                mat.roughness = 0.5;
                break;
        }

        return mat;
    }
    public enum MaterialPreset {

        GOLD, SILVER, COPPER, IRON, ALUMINUM, CHROME,
        PLASTIC_RED, PLASTIC_WHITE, PLASTIC_BLACK,
        RUBBER, WOOD, MARBLE, CONCRETE, GLASS, WATER, SKIN, FABRIC,
        EMISSIVE_WHITE, EMISSIVE_BLUE,
        CLAY
    }
    public Vector3 sampleAlbedo(double u, double v) {
        Vector3 color = albedoColor;

        if (albedoTexture != null) {
            int texColor = albedoTexture.sampleRGB(u, v);
            double r = ((texColor >> 16) & 0xFF) / 255.0;
            double g = ((texColor >> 8) & 0xFF) / 255.0;
            double b = (texColor & 0xFF) / 255.0;
            color = new Vector3(color.x * r, color.y * g, color.z * b);
        }

        return color;
    }
    public double sampleRoughness(double u, double v) {
        if (roughnessTexture != null) {
            int texColor = roughnessTexture.sampleRGB(u, v);
            return ((texColor >> 8) & 0xFF) / 255.0;
        }
        return roughness;
    }
    public double sampleMetalness(double u, double v) {
        if (metalnessTexture != null) {
            int texColor = metalnessTexture.sampleRGB(u, v);
            return (texColor & 0xFF) / 255.0;
        }
        return metalness;
    }
    public Vector3 sampleNormal(double u, double v) {
        if (normalTexture == null) {
            return new Vector3(0, 0, 1);
        }

        int texColor = normalTexture.sampleRGB(u, v);
        double nx = (((texColor >> 16) & 0xFF) / 255.0) * 2.0 - 1.0;
        double ny = (((texColor >> 8) & 0xFF) / 255.0) * 2.0 - 1.0;
        double nz = ((texColor & 0xFF) / 255.0) * 2.0 - 1.0;
        nx *= normalIntensity;
        ny *= normalIntensity;
        nz = Math.sqrt(Math.max(0, 1.0 - nx*nx - ny*ny));

        return new Vector3(nx, ny, nz).normalize();
    }
    public double sampleAO(double u, double v) {
        if (aoTexture != null) {
            int texColor = aoTexture.sampleRGB(u, v);
            double ao = ((texColor >> 16) & 0xFF) / 255.0;
            return lerp(1.0, ao, aoIntensity);
        }
        return 1.0;
    }
    public Vector3 sampleEmission(double u, double v) {
        Vector3 emission = emissionColor.scale(emissionIntensity);

        if (emissionTexture != null) {
            int texColor = emissionTexture.sampleRGB(u, v);
            double r = ((texColor >> 16) & 0xFF) / 255.0;
            double g = ((texColor >> 8) & 0xFF) / 255.0;
            double b = (texColor & 0xFF) / 255.0;

            emission = new Vector3(emission.x * r, emission.y * g, emission.z * b);
        }

        return emission;
    }
    public Vector3 computeF0(Vector3 albedo, double metalness) {
        Vector3 dielectricF0 = new Vector3(specularF0, specularF0, specularF0);
        return lerp(dielectricF0, albedo, metalness);
    }
    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private Vector3 lerp(Vector3 a, Vector3 b, double t) {
        return new Vector3(
            lerp(a.x, b.x, t),
            lerp(a.y, b.y, t),
            lerp(a.z, b.z, t)
        );
    }

    //Getters / Setters

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Vector3 getAlbedoColor() { return albedoColor; }
    public void setAlbedoColor(Vector3 color) { this.albedoColor = color; }
    public void setAlbedoColor(double r, double g, double b) { this.albedoColor = new Vector3(r, g, b); }

    public double getMetalness() { return metalness; }
    public void setMetalness(double metalness) { this.metalness = Math.max(0, Math.min(1, metalness)); }

    public double getRoughness() { return roughness; }
    public void setRoughness(double roughness) { this.roughness = Math.max(0.01, Math.min(1, roughness)); }

    public Vector3 getEmissionColor() { return emissionColor; }
    public void setEmissionColor(Vector3 color) { this.emissionColor = color; }

    public double getEmissionIntensity() { return emissionIntensity; }
    public void setEmissionIntensity(double intensity) { this.emissionIntensity = Math.max(0, intensity); }

    public double getAoIntensity() { return aoIntensity; }
    public void setAoIntensity(double intensity) { this.aoIntensity = Math.max(0, Math.min(1, intensity)); }

    public double getSpecularF0() { return specularF0; }
    public void setSpecularF0(double f0) { this.specularF0 = Math.max(0, Math.min(1, f0)); }

    public double getNormalIntensity() { return normalIntensity; }
    public void setNormalIntensity(double intensity) { this.normalIntensity = Math.max(0, intensity); }

    public double getIor() { return ior; }
    public void setIor(double ior) { this.ior = Math.max(1.0, ior); }

    public Texture getAlbedoTexture() { return albedoTexture; }
    public void setAlbedoTexture(Texture texture) { this.albedoTexture = texture; }

    public Texture getNormalTexture() { return normalTexture; }
    public void setNormalTexture(Texture texture) { this.normalTexture = texture; }

    public Texture getRoughnessTexture() { return roughnessTexture; }
    public void setRoughnessTexture(Texture texture) { this.roughnessTexture = texture; }

    public Texture getMetalnessTexture() { return metalnessTexture; }
    public void setMetalnessTexture(Texture texture) { this.metalnessTexture = texture; }

    public Texture getAoTexture() { return aoTexture; }
    public void setAoTexture(Texture texture) { this.aoTexture = texture; }

    public Texture getEmissionTexture() { return emissionTexture; }
    public void setEmissionTexture(Texture texture) { this.emissionTexture = texture; }

    public boolean isDoubleSided() { return doubleSided; }
    public void setDoubleSided(boolean doubleSided) { this.doubleSided = doubleSided; }

    public AlphaMode getAlphaMode() { return alphaMode; }
    public void setAlphaMode(AlphaMode mode) { this.alphaMode = mode; }

    public double getAlphaCutoff() { return alphaCutoff; }
    public void setAlphaCutoff(double cutoff) { this.alphaCutoff = Math.max(0, Math.min(1, cutoff)); }

    public boolean isUnlit() { return unlit; }
    public void setUnlit(boolean unlit) { this.unlit = unlit; }

    @Override
    public String toString() {
        return String.format("Material[%s: metal=%.2f, rough=%.2f, albedo=%s]",
            name, metalness, roughness, albedoColor);
    }
}

