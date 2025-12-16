package renderer;

public class GBuffer {

    private final int width;
    private final int height;
    private final int size;
    private final double[] depth;
    private final double[] normalX;
    private final double[] normalY;
    private final double[] normalZ;
    private final double[] tangentX;
    private final double[] tangentY;
    private final double[] tangentZ;
    private final int[] albedoR;
    private final int[] albedoG;
    private final int[] albedoB;
    private final double[] roughness;
    private final double[] metalness;
    private final double[] uvU;
    private final double[] uvV;
    private final boolean[] hasGeometry;

    public GBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.size = width * height;
        depth = new double[size];

        normalX = new double[size];
        normalY = new double[size];
        normalZ = new double[size];

        tangentX = new double[size];
        tangentY = new double[size];
        tangentZ = new double[size];

        albedoR = new int[size];
        albedoG = new int[size];
        albedoB = new int[size];

        roughness = new double[size];
        metalness = new double[size];

        uvU = new double[size];
        uvV = new double[size];

        hasGeometry = new boolean[size];
    }

    public void clear() {
        java.util.Arrays.fill(depth, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(hasGeometry, false);
    }
    public boolean write(int x, int y, double z,
                         Vector3 normal, Vector3 tangent,
                         int albR, int albG, int albB,
                         double rough, double metal,
                         double u, double v) {

        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false;
        }

        int idx = y * width + x;
        if (z >= depth[idx]) {
            return false;
        }
        depth[idx] = z;

        if (normal != null) {
            normalX[idx] = normal.x;
            normalY[idx] = normal.y;
            normalZ[idx] = normal.z;
        } else {
            normalX[idx] = 0;
            normalY[idx] = 0;
            normalZ[idx] = 1;
        }

        if (tangent != null) {
            tangentX[idx] = tangent.x;
            tangentY[idx] = tangent.y;
            tangentZ[idx] = tangent.z;
        } else {
            tangentX[idx] = 1;
            tangentY[idx] = 0;
            tangentZ[idx] = 0;
        }

        albedoR[idx] = albR;
        albedoG[idx] = albG;
        albedoB[idx] = albB;

        roughness[idx] = rough;
        metalness[idx] = metal;

        uvU[idx] = u;
        uvV[idx] = v;

        hasGeometry[idx] = true;

        return true;
    }
    public boolean hasData(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false;
        }
        return hasGeometry[y * width + x];
    }
    public double getDepth(int x, int y) {
        return depth[y * width + x];
    }
    public Vector3 getNormal(int x, int y) {
        int idx = y * width + x;
        return new Vector3(normalX[idx], normalY[idx], normalZ[idx]);
    }
    public Vector3 getTangent(int x, int y) {
        int idx = y * width + x;
        return new Vector3(tangentX[idx], tangentY[idx], tangentZ[idx]);
    }
    public int getAlbedoRGB(int x, int y) {
        int idx = y * width + x;
        return (0xFF << 24) | (albedoR[idx] << 16) | (albedoG[idx] << 8) | albedoB[idx];
    }
    public int[] getAlbedo(int x, int y) {
        int idx = y * width + x;
        return new int[] { albedoR[idx], albedoG[idx], albedoB[idx] };
    }
    public double getRoughness(int x, int y) {
        return roughness[y * width + x];
    }
    public double getMetalness(int x, int y) {
        return metalness[y * width + x];
    }
    public Vector2 getUV(int x, int y) {
        int idx = y * width + x;
        return new Vector2(uvU[idx], uvV[idx]);
    }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public long getMemoryUsage() {
        return (long) size * (8 + 24 + 24 + 12 + 16 + 16 + 1);
    }
    public String getMemoryUsageString() {
        long bytes = getMemoryUsage();
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}

