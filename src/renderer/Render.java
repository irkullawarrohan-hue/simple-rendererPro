package renderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
public class Render {
    private final int width;
    private final int height;
    private final BufferedImage image;
    private final double[] zbuffer;
    private final int[] frameBuffer;
    private boolean wireframe = false;
    public enum DebugMode {
        NONE,
        DEPTH,
        NORMALS,
        TANGENTS,
        UVS,
        OVERDRAW
    }
    private volatile DebugMode debugMode = DebugMode.NONE;
    private int[] overdrawBuffer = null;
    private final GBuffer gBuffer;
    private boolean deferredMode = false;
    private boolean accurateColorSpace = true;
    private int lastTrianglesTotal = 0;
    private int lastTrianglesDrawn = 0;
    private int lastTrianglesCulledBackface = 0;
    private int lastTrianglesCulledFrustum = 0;
    private long lastRenderTimeNs = 0;
    private static final int TILE_SIZE = 64;
    private final int numThreads;
    private final ExecutorService executor;
    private volatile boolean parallelEnabled = true;
    private int lastTilesProcessed = 0;
    private final Object[] tileLocks;
    private static class TransformedTriangle {
        final Vector3 v0, v1, v2;
        final Vector3 n0, n1, n2;
        final Vector3 t0, t1, t2;
        final Vector2 uv0, uv1, uv2;
        final int color;
        final int originalIndex;

        TransformedTriangle(Vector3 v0, Vector3 v1, Vector3 v2,
                           Vector3 n0, Vector3 n1, Vector3 n2,
                           Vector3 t0, Vector3 t1, Vector3 t2,
                           Vector2 uv0, Vector2 uv1, Vector2 uv2,
                           int color, int originalIndex) {
            this.v0 = v0; this.v1 = v1; this.v2 = v2;
            this.n0 = n0; this.n1 = n1; this.n2 = n2;
            this.t0 = t0; this.t1 = t1; this.t2 = t2;
            this.uv0 = uv0; this.uv1 = uv1; this.uv2 = uv2;
            this.color = color;
            this.originalIndex = originalIndex;
        }
    }
    private static class ProjectedTriangle {
        final ScreenVertex p0, p1, p2;
        final int color;

        ProjectedTriangle(ScreenVertex p0, ScreenVertex p1, ScreenVertex p2, int color) {
            this.p0 = p0;
            this.p1 = p1;
            this.p2 = p2;
            this.color = color;
        }
    }
    private static class Fragment {
        final int x, y;
        final double z;
        final Vector3 normal;
        final Vector3 tangent;
        final Vector2 uv;
        final int baseColor;

        Fragment(int x, int y, double z, Vector3 normal, Vector3 tangent, Vector2 uv, int baseColor) {
            this.x = x; this.y = y; this.z = z;
            this.normal = normal; this.tangent = tangent;
            this.uv = uv; this.baseColor = baseColor;
        }
    }
    private volatile double specularStrength = 1.0;
    private volatile double shininess = 32.0;
    private volatile double roughness = 0.35;
    private volatile double metalness = 0.0;
    private volatile double envStrength = 0.3;
    private volatile Texture albedoTex = null;
    private volatile Texture normalTex = null;
    private volatile EnvironmentMap environmentMap = null;
    private volatile boolean useIBL = true;
    private Vector3 lightDir;
    private Vector3 viewDir;
    private double specLocal, roughLocal, metalLocal, envLocal;
    private Texture albedoTexLocal, normalTexLocal;
    private EnvironmentMap envMapLocal;
    private boolean useIBLLocal;

    public Render(int width, int height) {
        this.width = width;
        this.height = height;
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.zbuffer = new double[width * height];
        this.frameBuffer = new int[width * height];
        this.gBuffer = new GBuffer(width, height);
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        this.numThreads = Math.max(1, Math.min(availableProcessors - 1, availableProcessors));
        this.executor = Executors.newFixedThreadPool(numThreads, r -> {
            Thread t = new Thread(r, "Rasterizer-Worker");
            t.setDaemon(true); // Don't prevent JVM shutdown
            return t;
        });
        int tilesX = (width + TILE_SIZE - 1) / TILE_SIZE;
        int tilesY = (height + TILE_SIZE - 1) / TILE_SIZE;
        this.tileLocks = new Object[tilesX * tilesY];
        for (int i = 0; i < tileLocks.length; i++) {
            tileLocks[i] = new Object();
        }

        System.out.println("Render: Initialized with " + numThreads + " worker threads, " +
                           tilesX + "x" + tilesY + " tiles (" + TILE_SIZE + "px each)");
    }

    //public API
    public BufferedImage getImage() { return image; }
    public void setWireframe(boolean wf) { this.wireframe = wf; }
    public boolean isWireframe() { return wireframe; }
    public void setDeferredMode(boolean deferred) { this.deferredMode = deferred; }
    public boolean isDeferredMode() { return deferredMode; }
    public void setAccurateColorSpace(boolean accurate) { this.accurateColorSpace = accurate; }
    public boolean isAccurateColorSpace() { return accurateColorSpace; }
    public String getLastFrameStats() {
        String mode = deferredMode ? "DEF" : "FWD";
        String colorMode = accurateColorSpace ? "sRGB" : "G2";
        String iblMode = (environmentMap != null && environmentMap.isLoaded() && useIBL) ? "IBL" : "Sky";
        String threadMode = parallelEnabled ? numThreads + "T" : "1T";
        String dbgMode = debugMode != DebugMode.NONE ? " [" + debugMode.name() + "]" : "";
        String gbufMem = deferredMode ? " GBuf:" + gBuffer.getMemoryUsageString() : "";
        return String.format("[%s|%s|%s|%s] Tris:%d/%d Cull:%d/%d %.1fms%s%s",
                mode, colorMode, iblMode, threadMode,
                lastTrianglesDrawn, lastTrianglesTotal,
                lastTrianglesCulledBackface, lastTrianglesCulledFrustum,
                lastRenderTimeNs / 1_000_000.0, dbgMode, gbufMem);
    }
    public void setSpecularStrength(double s) {
        this.specularStrength = Math.max(0.0, Math.min(1.0, s));
    }
    public double getSpecularStrength() { return this.specularStrength; }
    public void setShininess(double s) {
        this.shininess = Math.max(1.0, Math.min(128.0, s));
        this.roughness = Math.max(0.01, 1.0 - Math.sqrt(this.shininess / 128.0));
    }
    public double getShininess() { return this.shininess; }
    public void setRoughness(double r) {
        this.roughness = Math.max(0.01, Math.min(1.0, r));
    }
    public double getRoughness() { return this.roughness; }
    public void setMetalness(double m) {
        this.metalness = Math.max(0.0, Math.min(1.0, m));
    }
    public double getMetalness() { return this.metalness; }
    public void setEnvStrength(double e) {
        this.envStrength = Math.max(0.0, Math.min(1.0, e));
    }
    public double getEnvStrength() { return this.envStrength; }

    public void setAlbedoTexture(Texture t) { this.albedoTex = t; }
    public Texture getAlbedoTexture() { return this.albedoTex; }
    public void setNormalTexture(Texture t) { this.normalTex = t; }
    public Texture getNormalTexture() { return this.normalTex; }
    public void setEnvironmentMap(EnvironmentMap env) { this.environmentMap = env; }
    public EnvironmentMap getEnvironmentMap() { return this.environmentMap; }
    public void setUseIBL(boolean use) { this.useIBL = use; }
    public boolean isUseIBL() { return this.useIBL; }
    public void setParallelEnabled(boolean enabled) { this.parallelEnabled = enabled; }
    public boolean isParallelEnabled() { return this.parallelEnabled; }
    public int getNumThreads() { return this.numThreads; }
    public void setDebugMode(DebugMode mode) {
        this.debugMode = mode;
        if (mode == DebugMode.OVERDRAW && overdrawBuffer == null) {
            overdrawBuffer = new int[width * height];
        }
    }
    public DebugMode getDebugMode() { return this.debugMode; }
    public void cycleDebugMode() {
        DebugMode[] modes = DebugMode.values();
        int next = (debugMode.ordinal() + 1) % modes.length;
        setDebugMode(modes[next]);
    }
    public String getDebugModeName() {
        return debugMode == DebugMode.NONE ? "Shaded" : debugMode.name();
    }
    public void clear() {
        int bg = 0xFF2B2B2B;
        java.util.Arrays.fill(frameBuffer, bg);
        java.util.Arrays.fill(zbuffer, Double.POSITIVE_INFINITY);
        if (debugMode == DebugMode.OVERDRAW && overdrawBuffer != null) {
            java.util.Arrays.fill(overdrawBuffer, 0);
        }
        image.setRGB(0, 0, width, height, frameBuffer, 0, width);
    }
    public void render(Mesh mesh, Camera camera, double modelAngle) {
        if (mesh == null) return;

        long renderStart = System.nanoTime();
        initFrameState(mesh);
        List<TransformedTriangle> transformed = stageGeometryTransform(mesh, camera, modelAngle);
        List<TransformedTriangle> visible = stageCulling(transformed, camera);
        List<ProjectedTriangle> projected = stageProjection(visible, camera);
        if (deferredMode) {
            stageDeferredGeometryPass(projected);
            stageDeferredLightingPass();
        } else {
            stageRasterizeAndShade(projected);
        }
        if (debugMode == DebugMode.OVERDRAW && overdrawBuffer != null) {
            applyOverdrawVisualization();
        }
        image.setRGB(0, 0, width, height, frameBuffer, 0, width);
        if (wireframe) {
            drawWireframeOverlay(projected);
        }

        lastRenderTimeNs = System.nanoTime() - renderStart;
    }
    private void applyOverdrawVisualization() {
        for (int i = 0; i < frameBuffer.length; i++) {
            frameBuffer[i] = overdrawToColor(overdrawBuffer[i]);
        }
    }
    private List<TransformedTriangle> stageGeometryTransform(Mesh mesh, Camera camera, double modelAngle) {
        List<TransformedTriangle> result = new ArrayList<>(mesh.triangles.size());

        for (int i = 0; i < mesh.triangles.size(); i++) {
            Triangle t = mesh.triangles.get(i);

            Vector3 mv0 = rotateY(t.v0, modelAngle);
            Vector3 mv1 = rotateY(t.v1, modelAngle);
            Vector3 mv2 = rotateY(t.v2, modelAngle);
            Vector3 cv0 = camera.worldToView(mv0);
            Vector3 cv1 = camera.worldToView(mv1);
            Vector3 cv2 = camera.worldToView(mv2);
            Vector3 rn0 = t.n0 != null ? rotateY(t.n0, modelAngle).normalize() : null;
            Vector3 rn1 = t.n1 != null ? rotateY(t.n1, modelAngle).normalize() : null;
            Vector3 rn2 = t.n2 != null ? rotateY(t.n2, modelAngle).normalize() : null;
            Vector3 rt0 = t.t0 != null ? rotateY(t.t0, modelAngle).normalize() : new Vector3(1, 0, 0);
            Vector3 rt1 = t.t1 != null ? rotateY(t.t1, modelAngle).normalize() : new Vector3(1, 0, 0);
            Vector3 rt2 = t.t2 != null ? rotateY(t.t2, modelAngle).normalize() : new Vector3(1, 0, 0);

            result.add(new TransformedTriangle(
                cv0, cv1, cv2,
                rn0, rn1, rn2,
                rt0, rt1, rt2,
                t.uv0, t.uv1, t.uv2,
                t.color, i
            ));
        }

        return result;
    }
    private List<TransformedTriangle> stageCulling(List<TransformedTriangle> triangles, Camera camera) {
        List<TransformedTriangle> visible = new ArrayList<>();

        for (TransformedTriangle t : triangles) {
            if (cullFrustum(t.v0, t.v1, t.v2, camera)) {
                lastTrianglesCulledFrustum++;
                continue;
            }
            CullResult backfaceResult = cullBackface(t.v0, t.v1, t.v2);
            if (backfaceResult == CullResult.CULLED) {
                lastTrianglesCulledBackface++;
                continue;
            }
            if (backfaceResult == CullResult.DEGENERATE) {
                continue;
            }

            visible.add(t);
        }

        return visible;
    }

    private enum CullResult { VISIBLE, CULLED, DEGENERATE }
    private CullResult cullBackface(Vector3 v0, Vector3 v1, Vector3 v2) {
        Vector3 faceNormal = v1.sub(v0).cross(v2.sub(v0));
        double fnLen = faceNormal.length();

        if (fnLen < 1e-9) {
            return CullResult.DEGENERATE;
        }
        Vector3 toCamera = new Vector3(0, 0, -1);
        if (faceNormal.scale(1.0 / fnLen).dot(toCamera) <= 0) {
            return CullResult.CULLED;
        }

        return CullResult.VISIBLE;
    }
    private boolean cullFrustum(Vector3 v0, Vector3 v1, Vector3 v2, Camera camera) {
        double minZ = Math.min(v0.z, Math.min(v1.z, v2.z));
        double maxZ = Math.max(v0.z, Math.max(v1.z, v2.z));
        if (maxZ < camera.near || minZ > camera.far) {
            return true;
        }
        double testZ = Math.max(camera.near, minZ);
        double fovRad = Math.toRadians(camera.fov);
        double halfTan = Math.tan(fovRad * 0.5);
        double halfWidth = halfTan * camera.aspect * testZ * 1.2;
        double halfHeight = halfTan * testZ * 1.2;
        double minX = Math.min(v0.x, Math.min(v1.x, v2.x));
        double maxX = Math.max(v0.x, Math.max(v1.x, v2.x));
        double minY = Math.min(v0.y, Math.min(v1.y, v2.y));
        double maxY = Math.max(v0.y, Math.max(v1.y, v2.y));

        return (maxX < -halfWidth || minX > halfWidth ||
                maxY < -halfHeight || minY > halfHeight);
    }
    private List<ProjectedTriangle> stageProjection(List<TransformedTriangle> triangles, Camera camera) {
        List<ProjectedTriangle> projected = new ArrayList<>(triangles.size());

        for (TransformedTriangle t : triangles) {
            ScreenVertex p0 = projectVertex(t.v0, t.n0, t.t0, t.uv0, camera);
            ScreenVertex p1 = projectVertex(t.v1, t.n1, t.t1, t.uv1, camera);
            ScreenVertex p2 = projectVertex(t.v2, t.n2, t.t2, t.uv2, camera);

            projected.add(new ProjectedTriangle(p0, p1, p2, t.color));
        }

        return projected;
    }
    private ScreenVertex projectVertex(Vector3 viewPos, Vector3 normal, Vector3 tangent,
                                       Vector2 uv, Camera camera) {
        Vector3 p = camera.project(viewPos, width, height);
        double z = Math.max(0.0001, viewPos.z);

        return new ScreenVertex((int) p.x, (int) p.y, z, normal, tangent, uv);
    }
    private void stageRasterizeAndShade(List<ProjectedTriangle> triangles) {
        if (parallelEnabled && numThreads > 1 && triangles.size() > 10) {
            stageRasterizeAndShadeParallel(triangles);
        } else {
            stageRasterizeAndShadeSingleThread(triangles);
        }
    }
    private void stageRasterizeAndShadeSingleThread(List<ProjectedTriangle> triangles) {
        for (ProjectedTriangle tri : triangles) {
            rasterizeTriangle(tri);
            lastTrianglesDrawn++;
        }
        lastTilesProcessed = 0;
    }
    private void stageRasterizeAndShadeParallel(List<ProjectedTriangle> triangles) {
        int tilesX = (width + TILE_SIZE - 1) / TILE_SIZE;
        int tilesY = (height + TILE_SIZE - 1) / TILE_SIZE;
        int totalTiles = tilesX * tilesY;
        List<ProjectedTriangle>[] tileLists = new List[totalTiles];
        for (int i = 0; i < totalTiles; i++) {
            tileLists[i] = new ArrayList<>();
        }
        for (ProjectedTriangle tri : triangles) {
            int minX = Math.max(0, Math.min(tri.p0.x, Math.min(tri.p1.x, tri.p2.x)));
            int maxX = Math.min(width - 1, Math.max(tri.p0.x, Math.max(tri.p1.x, tri.p2.x)));
            int minY = Math.max(0, Math.min(tri.p0.y, Math.min(tri.p1.y, tri.p2.y)));
            int maxY = Math.min(height - 1, Math.max(tri.p0.y, Math.max(tri.p1.y, tri.p2.y)));
            int tileMinX = minX / TILE_SIZE;
            int tileMaxX = maxX / TILE_SIZE;
            int tileMinY = minY / TILE_SIZE;
            int tileMaxY = maxY / TILE_SIZE;
            for (int ty = tileMinY; ty <= tileMaxY; ty++) {
                for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                    int tileIdx = ty * tilesX + tx;
                    if (tileIdx >= 0 && tileIdx < totalTiles) {
                        tileLists[tileIdx].add(tri);
                    }
                }
            }
        }
        AtomicInteger tilesProcessed = new AtomicInteger(0);
        AtomicInteger trianglesDrawn = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalTiles);

        for (int tileIdx = 0; tileIdx < totalTiles; tileIdx++) {
            final int idx = tileIdx;
            final int tileX = idx % tilesX;
            final int tileY = idx / tilesX;
            final List<ProjectedTriangle> tileTriangles = tileLists[idx];

            executor.submit(() -> {
                try {
                    if (!tileTriangles.isEmpty()) {
                        // Calculate tile bounds
                        int tileStartX = tileX * TILE_SIZE;
                        int tileStartY = tileY * TILE_SIZE;
                        int tileEndX = Math.min(tileStartX + TILE_SIZE, width);
                        int tileEndY = Math.min(tileStartY + TILE_SIZE, height);
                        for (ProjectedTriangle tri : tileTriangles) {
                            rasterizeTriangleTile(tri, tileStartX, tileStartY, tileEndX, tileEndY);
                        }

                        tilesProcessed.incrementAndGet();
                        trianglesDrawn.addAndGet(tileTriangles.size());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        lastTilesProcessed = tilesProcessed.get();
        lastTrianglesDrawn = triangles.size();
    }
    private void rasterizeTriangleTile(ProjectedTriangle tri,
                                        int tileStartX, int tileStartY,
                                        int tileEndX, int tileEndY) {
        ScreenVertex p0 = tri.p0, p1 = tri.p1, p2 = tri.p2;
        int minX = Math.max(tileStartX, Math.min(p0.x, Math.min(p1.x, p2.x)));
        int maxX = Math.min(tileEndX - 1, Math.max(p0.x, Math.max(p1.x, p2.x)));
        int minY = Math.max(tileStartY, Math.min(p0.y, Math.min(p1.y, p2.y)));
        int maxY = Math.min(tileEndY - 1, Math.max(p0.y, Math.max(p1.y, p2.y)));

        if (minX > maxX || minY > maxY) return;
        double ax = p0.x, ay = p0.y;
        double bx = p1.x, by = p1.y;
        double cx = p2.x, cy = p2.y;

        double denom = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay);
        if (Math.abs(denom) < 1e-9) return;
        for (int y = minY; y <= maxY; y++) {
            int row = y * width;
            for (int x = minX; x <= maxX; x++) {
                double v = ((x - ax) * (cy - ay) - (cx - ax) * (y - ay)) / denom;
                double w = ((bx - ax) * (y - ay) - (x - ax) * (by - ay)) / denom;
                double u = 1.0 - v - w;
                if (u >= -1e-6 && v >= -1e-6 && w >= -1e-6) {
                    double z = u * p0.z + v * p1.z + w * p2.z;
                    int idx = row + x;
                    if (z < zbuffer[idx]) {
                        Fragment frag = interpolateFragment(x, y, z, u, v, w, p0, p1, p2, tri.color);
                        int color = shadeFragment(frag);
                        Object lock = tileLocks[(idx >> 6) % tileLocks.length];
                        synchronized (lock) {
                            if (z < zbuffer[idx]) {
                                zbuffer[idx] = z;
                                frameBuffer[idx] = color;
                            }
                        }
                    }
                }
            }
        }
    }
    private void rasterizeTriangle(ProjectedTriangle tri) {
        ScreenVertex p0 = tri.p0, p1 = tri.p1, p2 = tri.p2;


        int minX = Math.max(0, Math.min(p0.x, Math.min(p1.x, p2.x)));
        int maxX = Math.min(width - 1, Math.max(p0.x, Math.max(p1.x, p2.x)));
        int minY = Math.max(0, Math.min(p0.y, Math.min(p1.y, p2.y)));
        int maxY = Math.min(height - 1, Math.max(p0.y, Math.max(p1.y, p2.y)));
        double ax = p0.x, ay = p0.y;
        double bx = p1.x, by = p1.y;
        double cx = p2.x, cy = p2.y;

        double denom = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay);
        if (Math.abs(denom) < 1e-9) return;
        boolean trackOverdraw = (debugMode == DebugMode.OVERDRAW && overdrawBuffer != null);
        for (int y = minY; y <= maxY; y++) {
            int row = y * width;
            for (int x = minX; x <= maxX; x++) {
                double v = ((x - ax) * (cy - ay) - (cx - ax) * (y - ay)) / denom;
                double w = ((bx - ax) * (y - ay) - (x - ax) * (by - ay)) / denom;
                double u = 1.0 - v - w;

                if (u >= -1e-6 && v >= -1e-6 && w >= -1e-6) {
                    double z = u * p0.z + v * p1.z + w * p2.z;
                    int idx = row + x;
                    if (trackOverdraw) {
                        overdrawBuffer[idx]++;
                    }
                    if (z < zbuffer[idx]) {
                        zbuffer[idx] = z;
                        Fragment frag = interpolateFragment(x, y, z, u, v, w, p0, p1, p2, tri.color);
                        int color = shadeFragment(frag);
                        frameBuffer[idx] = color;
                    }
                }
            }
        }
    }
    private Fragment interpolateFragment(int x, int y, double z,
                                         double u, double v, double w,
                                         ScreenVertex p0, ScreenVertex p1, ScreenVertex p2,
                                         int baseColor) {
        Vector3 normal;
        if (p0.n != null && p1.n != null && p2.n != null) {
            normal = p0.n.scale(u).add(p1.n.scale(v)).add(p2.n.scale(w)).normalize();
        } else {
            normal = new Vector3(0, 0, 1);
        }


        Vector3 tangent = null;
        if (p0.t != null && p1.t != null && p2.t != null) {
            tangent = p0.t.scale(u).add(p1.t.scale(v)).add(p2.t.scale(w)).normalize();
        }
        Vector2 uv = null;
        if (p0.uv != null && p1.uv != null && p2.uv != null) {
            double tu = u * p0.uv.u + v * p1.uv.u + w * p2.uv.u;
            double tv = u * p0.uv.v + v * p1.uv.v + w * p2.uv.v;
            uv = new Vector2(tu, tv);
        }

        return new Fragment(x, y, z, normal, tangent, uv, baseColor);
    }
    private void stageDeferredGeometryPass(List<ProjectedTriangle> triangles) {

        gBuffer.clear();

        for (ProjectedTriangle tri : triangles) {
            rasterizeToGBuffer(tri);
            lastTrianglesDrawn++;
        }
    }
    private void rasterizeToGBuffer(ProjectedTriangle tri) {
        ScreenVertex p0 = tri.p0, p1 = tri.p1, p2 = tri.p2;
        int minX = Math.max(0, Math.min(p0.x, Math.min(p1.x, p2.x)));
        int maxX = Math.min(width - 1, Math.max(p0.x, Math.max(p1.x, p2.x)));
        int minY = Math.max(0, Math.min(p0.y, Math.min(p1.y, p2.y)));
        int maxY = Math.min(height - 1, Math.max(p0.y, Math.max(p1.y, p2.y)));
        double ax = p0.x, ay = p0.y;
        double bx = p1.x, by = p1.y;
        double cx = p2.x, cy = p2.y;

        double denom = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay);
        if (Math.abs(denom) < 1e-9) return;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                // Barycentric coordinates
                double v = ((x - ax) * (cy - ay) - (cx - ax) * (y - ay)) / denom;
                double w = ((bx - ax) * (y - ay) - (x - ax) * (by - ay)) / denom;
                double u = 1.0 - v - w;

                // Inside triangle test
                if (u >= -1e-6 && v >= -1e-6 && w >= -1e-6) {
                    // Interpolate depth
                    double z = u * p0.z + v * p1.z + w * p2.z;

                    // Interpolate normal
                    Vector3 normal;
                    if (p0.n != null && p1.n != null && p2.n != null) {
                        normal = p0.n.scale(u).add(p1.n.scale(v)).add(p2.n.scale(w)).normalize();
                    } else {
                        normal = new Vector3(0, 0, 1);
                    }
                    Vector3 tangent = null;
                    if (p0.t != null && p1.t != null && p2.t != null) {
                        tangent = p0.t.scale(u).add(p1.t.scale(v)).add(p2.t.scale(w)).normalize();
                    }
                    double tu = 0, tv = 0;
                    if (p0.uv != null && p1.uv != null && p2.uv != null) {
                        tu = u * p0.uv.u + v * p1.uv.u + w * p2.uv.u;
                        tv = u * p0.uv.v + v * p1.uv.v + w * p2.uv.v;
                    }
                    int albR = (tri.color >> 16) & 0xFF;
                    int albG = (tri.color >> 8) & 0xFF;
                    int albB = tri.color & 0xFF;

                    if (albedoTexLocal != null && p0.uv != null && p1.uv != null && p2.uv != null) {
                        try {
                            int sample = albedoTexLocal.sampleRGB(tu, tv);
                            albR = (sample >> 16) & 0xFF;
                            albG = (sample >> 8) & 0xFF;
                            albB = sample & 0xFF;
                        } catch (Exception ignored) {}
                    }
                    gBuffer.write(x, y, z, normal, tangent,
                                  albR, albG, albB,
                                  roughLocal, metalLocal, tu, tv);
                }
            }
        }
    }
    private void stageDeferredLightingPass() {
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int idx = row + x;
                if (!gBuffer.hasData(x, y)) {
                    continue;
                }
                Vector3 normal = gBuffer.getNormal(x, y);
                Vector3 tangent = gBuffer.getTangent(x, y);
                int[] albedo = gBuffer.getAlbedo(x, y);
                double rough = gBuffer.getRoughness(x, y);
                double metal = gBuffer.getMetalness(x, y);
                Vector2 uv = gBuffer.getUV(x, y);
                if (normalTexLocal != null && tangent != null && uv != null) {
                    Vector3 perturbed = sampleAndTransformNormal(normalTexLocal, uv.u, uv.v, tangent, normal);
                    if (perturbed != null) {
                        normal = perturbed;
                    }
                }
                double[] litColor = computePBRLightingDeferred(normal, albedo[0], albedo[1], albedo[2], rough, metal);
                double[] finalColor = applyEnvironmentReflectionDeferred(normal,
                        litColor[0], litColor[1], litColor[2],
                        litColor[3], litColor[4], litColor[5],
                        rough);
                frameBuffer[idx] = toneMapAndEncode(finalColor[0], finalColor[1], finalColor[2], 0xFF);
            }
        }
    }
    private double[] computePBRLightingDeferred(Vector3 normal, int baseR, int baseG, int baseB,
                                                 double rough, double metal) {

        double[] linearAlbedo = srgbToLinearRGB(baseR, baseG, baseB);
        double albedoR = linearAlbedo[0];
        double albedoG = linearAlbedo[1];
        double albedoB = linearAlbedo[2];
        double dielectricF0 = 0.04 * specLocal;
        double F0r = dielectricF0 * (1.0 - metal) + albedoR * metal * specLocal;
        double F0g = dielectricF0 * (1.0 - metal) + albedoG * metal * specLocal;
        double F0b = dielectricF0 * (1.0 - metal) + albedoB * metal * specLocal;
        double ambientR, ambientG, ambientB;
        if (useIBLLocal) {
            double[] irradiance = envMapLocal.sampleDiffuse(normal);
            double ambientScale = envLocal * (1.0 - metal);
            ambientR = irradiance[0] * albedoR * ambientScale;
            ambientG = irradiance[1] * albedoG * ambientScale;
            ambientB = irradiance[2] * albedoB * ambientScale;

            double metalAmbient = 0.02 * metal * envLocal;
            ambientR += albedoR * metalAmbient;
            ambientG += albedoG * metalAmbient;
            ambientB += albedoB * metalAmbient;
        } else {
            double ambientFactor = 0.08 * (1.0 - metal) + 0.02 * metal;
            ambientR = albedoR * ambientFactor;
            ambientG = albedoG * ambientFactor;
            ambientB = albedoB * ambientFactor;
        }

        // Diffuse (Lambertian)
        double NdotL = Math.max(0.0, normal.dot(lightDir));
        double NdotV = Math.max(0.001, normal.dot(viewDir));
        double avgF0 = (F0r + F0g + F0b) / 3.0;
        double kS = fresnelSchlick(NdotV, avgF0);
        double kD = (1.0 - kS) * (1.0 - metal);

        double diffuseR = kD * albedoR * NdotL;
        double diffuseG = kD * albedoG * NdotL;
        double diffuseB = kD * albedoB * NdotL;

        // Specular (Cook-Torrance GGX) - use per-pixel roughness
        double[] specular = evaluateCookTorranceSpecular(normal, viewDir, lightDir, rough, F0r, F0g, F0b);

        // Combine
        double litR = ambientR + diffuseR + specular[0];
        double litG = ambientG + diffuseG + specular[1];
        double litB = ambientB + diffuseB + specular[2];

        return new double[] { litR, litG, litB, F0r, F0g, F0b };
    }
    private double[] applyEnvironmentReflectionDeferred(Vector3 normal,
                                                        double litR, double litG, double litB,
                                                        double F0r, double F0g, double F0b,
                                                        double rough) {
        if (envLocal <= 0.001) {
            return new double[] { litR, litG, litB };
        }

        double ndotv = Math.max(0.0, normal.dot(viewDir));
        Vector3 R = normal.scale(2.0 * ndotv).sub(viewDir).normalize();
        double envr, envg, envb;
        if (useIBLLocal) {
            double[] envSample = envMapLocal.sampleSpecularRough(R, rough);
            envr = envSample[0];
            envg = envSample[1];
            envb = envSample[2];
        } else {
            if (R.z < 0) R = new Vector3(R.x, R.y, -R.z);
            int[] env = sampleEnvironmentColor(R);
            double[] linearEnv = srgbToLinearRGB(env[0], env[1], env[2]);
            envr = linearEnv[0];
            envg = linearEnv[1];
            envb = linearEnv[2];
        }

        double[] envFresnel = fresnelSchlickRGB(ndotv, F0r, F0g, F0b);
        double roughnessFade = 1.0 - rough * rough;
        double envMix = envLocal * roughnessFade;

        return new double[] {
            litR + envFresnel[0] * envr * envMix,
            litG + envFresnel[1] * envg * envMix,
            litB + envFresnel[2] * envb * envMix
        };
    }
    private void initFrameState(Mesh mesh) {
        lastTrianglesTotal = mesh.triangles.size();
        lastTrianglesDrawn = 0;
        lastTrianglesCulledBackface = 0;
        lastTrianglesCulledFrustum = 0;
        specLocal = this.specularStrength;
        roughLocal = this.roughness;
        metalLocal = this.metalness;
        envLocal = this.envStrength;
        albedoTexLocal = this.albedoTex;
        normalTexLocal = this.normalTex;
        envMapLocal = this.environmentMap;
        useIBLLocal = this.useIBL && envMapLocal != null && envMapLocal.isLoaded();
        lightDir = new Vector3(0.6, 0.8, -1.0).normalize();
        viewDir = new Vector3(0, 0, 1);
    }
    private int shadeFragment(Fragment frag) {
        if (debugMode != DebugMode.NONE) {
            return shadeFragmentDebug(frag);
        }
        Vector3 normal = applyNormalMapping(frag);
        int[] albedo = sampleAlbedo(frag);
        int baseR = albedo[0], baseG = albedo[1], baseB = albedo[2];
        double[] litColor = computePBRLighting(normal, baseR, baseG, baseB);
        double[] finalColor = applyEnvironmentReflection(normal, litColor[0], litColor[1], litColor[2],
                                                         litColor[3], litColor[4], litColor[5]); // F0 values
        return toneMapAndEncode(finalColor[0], finalColor[1], finalColor[2],
                               (frag.baseColor >> 24) & 0xFF);
    }
    private int shadeFragmentDebug(Fragment frag) {
        switch (debugMode) {
            case DEPTH:
                return visualizeDepth(frag.z);

            case NORMALS:
                return visualizeNormal(frag.normal);

            case TANGENTS:
                return visualizeTangent(frag.tangent);

            case UVS:
                return visualizeUV(frag.uv);

            case OVERDRAW:
                return 0xFFFFFFFF;

            default:
                return 0xFF000000;
        }
    }
    private int visualizeDepth(double z) {
        double near = 0.1;
        double far = 100.0;
        z = Math.max(near, Math.min(far, z));
        double linearDepth = (z - near) / (far - near);
        double brightness = 1.0 - linearDepth;
        brightness = Math.pow(brightness, 0.5);

        int gray = (int) Math.round(brightness * 255);
        gray = Math.max(0, Math.min(255, gray));

        return 0xFF000000 | (gray << 16) | (gray << 8) | gray;
    }
    private int visualizeNormal(Vector3 normal) {
        if (normal == null) {
            return 0xFF8080FF;
        }
        int r = (int) Math.round((normal.x * 0.5 + 0.5) * 255);
        int g = (int) Math.round((normal.y * 0.5 + 0.5) * 255);
        int b = (int) Math.round((normal.z * 0.5 + 0.5) * 255);

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    private int visualizeTangent(Vector3 tangent) {
        if (tangent == null) {
            return 0xFFFF8080;
        }

        // Map from [-1, 1] to [0, 255]
        int r = (int) Math.round((tangent.x * 0.5 + 0.5) * 255);
        int g = (int) Math.round((tangent.y * 0.5 + 0.5) * 255);
        int b = (int) Math.round((tangent.z * 0.5 + 0.5) * 255);

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    private int visualizeUV(Vector2 uv) {
        if (uv == null) {
            return 0xFFFF00FF;
        }


        double u = uv.u - Math.floor(uv.u);
        double v = uv.v - Math.floor(uv.v);

        int r = (int) Math.round(u * 255);
        int g = (int) Math.round(v * 255);

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));

        return 0xFF000000 | (r << 16) | (g << 8) | 0;
    }
    private int overdrawToColor(int count) {
        if (count == 0) return 0xFF2B2B2B;
        if (count == 1) return 0xFF0000FF;
        if (count == 2) return 0xFF00FF00;
        if (count == 3) return 0xFF00FFFF;
        if (count <= 5) return 0xFFFFFF00;
        if (count <= 7) return 0xFFFF8000;
        return 0xFFFF0000; // Red - problematic
    }
    private Vector3 applyNormalMapping(Fragment frag) {
        Vector3 normal = frag.normal;

        if (normalTexLocal != null && frag.uv != null && frag.tangent != null) {
            Vector3 perturbed = sampleAndTransformNormal(normalTexLocal,
                                                         frag.uv.u, frag.uv.v,
                                                         frag.tangent, frag.normal);
            if (perturbed != null) {
                normal = perturbed;
            }
        }

        return normal;
    }
    private int[] sampleAlbedo(Fragment frag) {
        int baseR = (frag.baseColor >> 16) & 0xFF;
        int baseG = (frag.baseColor >> 8) & 0xFF;
        int baseB = frag.baseColor & 0xFF;

        if (frag.uv != null && albedoTexLocal != null) {
            try {
                int sample = albedoTexLocal.sampleRGB(frag.uv.u, frag.uv.v);
                baseR = (sample >> 16) & 0xFF;
                baseG = (sample >> 8) & 0xFF;
                baseB = sample & 0xFF;
            } catch (Exception ignored) {}
        }

        return new int[] { baseR, baseG, baseB };
    }
    private double[] computePBRLighting(Vector3 normal, int baseR, int baseG, int baseB) {
        double[] linearAlbedo = srgbToLinearRGB(baseR, baseG, baseB);
        double albedoR = linearAlbedo[0];
        double albedoG = linearAlbedo[1];
        double albedoB = linearAlbedo[2];
        double dielectricF0 = 0.04 * specLocal;
        double F0r = dielectricF0 * (1.0 - metalLocal) + albedoR * metalLocal * specLocal;
        double F0g = dielectricF0 * (1.0 - metalLocal) + albedoG * metalLocal * specLocal;
        double F0b = dielectricF0 * (1.0 - metalLocal) + albedoB * metalLocal * specLocal;
        double ambientR, ambientG, ambientB;
        if (useIBLLocal) {
            double[] irradiance = envMapLocal.sampleDiffuse(normal);
            double ambientScale = envLocal * (1.0 - metalLocal);
            ambientR = irradiance[0] * albedoR * ambientScale;
            ambientG = irradiance[1] * albedoG * ambientScale;
            ambientB = irradiance[2] * albedoB * ambientScale;
            double metalAmbient = 0.02 * metalLocal * envLocal;
            ambientR += albedoR * metalAmbient;
            ambientG += albedoG * metalAmbient;
            ambientB += albedoB * metalAmbient;
        } else {
            double ambientFactor = 0.08 * (1.0 - metalLocal) + 0.02 * metalLocal;
            ambientR = albedoR * ambientFactor;
            ambientG = albedoG * ambientFactor;
            ambientB = albedoB * ambientFactor;
        }
        double NdotL = Math.max(0.0, normal.dot(lightDir));
        double NdotV = Math.max(0.001, normal.dot(viewDir));
        double avgF0 = (F0r + F0g + F0b) / 3.0;
        double kS = fresnelSchlick(NdotV, avgF0);
        double kD = (1.0 - kS) * (1.0 - metalLocal);

        double diffuseR = kD * albedoR * NdotL;
        double diffuseG = kD * albedoG * NdotL;
        double diffuseB = kD * albedoB * NdotL;
        double[] specular = evaluateCookTorranceSpecular(normal, viewDir, lightDir, roughLocal, F0r, F0g, F0b);
        double litR = ambientR + diffuseR + specular[0];
        double litG = ambientG + diffuseG + specular[1];
        double litB = ambientB + diffuseB + specular[2];

        return new double[] { litR, litG, litB, F0r, F0g, F0b };
    }
    private double[] applyEnvironmentReflection(Vector3 normal,
                                                double litR, double litG, double litB,
                                                double F0r, double F0g, double F0b) {
        if (envLocal <= 0.001) {
            return new double[] { litR, litG, litB };
        }

        double ndotv = Math.max(0.0, normal.dot(viewDir));
        Vector3 R = normal.scale(2.0 * ndotv).sub(viewDir).normalize();

        // Sample environment (IBL or procedural)
        double envr, envg, envb;
        if (useIBLLocal) {
            // Use IBL environment map with roughness-based blur
            double[] envSample = envMapLocal.sampleSpecularRough(R, roughLocal);
            envr = envSample[0];
            envg = envSample[1];
            envb = envSample[2];
        } else {
            // Fallback to procedural sky
            if (R.z < 0) R = new Vector3(R.x, R.y, -R.z);
            int[] env = sampleEnvironmentColor(R);
            double[] linearEnv = srgbToLinearRGB(env[0], env[1], env[2]);
            envr = linearEnv[0];
            envg = linearEnv[1];
            envb = linearEnv[2];
        }

        double[] envFresnel = fresnelSchlickRGB(ndotv, F0r, F0g, F0b);
        double roughnessFade = 1.0 - roughLocal * roughLocal;
        double envMix = envLocal * roughnessFade;

        return new double[] {
            litR + envFresnel[0] * envr * envMix,
            litG + envFresnel[1] * envg * envMix,
            litB + envFresnel[2] * envb * envMix
        };
    }
    private int toneMapAndEncode(double r, double g, double b, int alpha) {
        r = r / (r + 1.0);
        g = g / (g + 1.0);
        b = b / (b + 1.0);
        int nr = linearToSrgbByte(r);
        int ng = linearToSrgbByte(g);
        int nb = linearToSrgbByte(b);

        return (alpha << 24) | (nr << 16) | (ng << 8) | nb;
    }
    private void drawWireframeOverlay(List<ProjectedTriangle> triangles) {
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1.0f));
            g.setColor(new Color(0xFFFFFFFF, true));

            for (ProjectedTriangle tri : triangles) {
                g.drawLine(clampX(tri.p0.x), clampY(tri.p0.y), clampX(tri.p1.x), clampY(tri.p1.y));
                g.drawLine(clampX(tri.p1.x), clampY(tri.p1.y), clampX(tri.p2.x), clampY(tri.p2.y));
                g.drawLine(clampX(tri.p2.x), clampY(tri.p2.y), clampX(tri.p0.x), clampY(tri.p0.y));
            }
        } finally {
            g.dispose();
        }
    }

    private Vector3 sampleAndTransformNormal(Texture normalTex, double u, double v, Vector3 T, Vector3 N) {
        if (normalTex == null || T == null || N == null) return null;

        try {
            int sample = normalTex.sampleRGB(u, v);
            double tx = ((sample >> 16) & 0xFF) / 255.0 * 2.0 - 1.0;
            double ty = ((sample >> 8) & 0xFF) / 255.0 * 2.0 - 1.0;
            double tz = (sample & 0xFF) / 255.0 * 2.0 - 1.0;

            Vector3 tangentNormal = new Vector3(tx, ty, tz).normalize();
            return transformTBN(tangentNormal, T, N);
        } catch (Exception e) {
            return null;
        }
    }

    private Vector3 transformTBN(Vector3 tangentNormal, Vector3 T, Vector3 N) {
        Vector3 B = N.cross(T).normalize();
        T = T.sub(N.scale(T.dot(N))).normalize();

        return T.scale(tangentNormal.x)
                .add(B.scale(tangentNormal.y))
                .add(N.scale(tangentNormal.z))
                .normalize();
    }
    private double srgbToLinear(double srgb) {
        if (accurateColorSpace) {
            if (srgb <= 0.04045) {
                return srgb / 12.92;
            } else {
                return Math.pow((srgb + 0.055) / 1.055, 2.4);
            }
        } else {
            return srgb * srgb;
        }
    }
    private double linearToSrgb(double linear) {
        if (accurateColorSpace) {
            if (linear <= 0.0031308) {
                return linear * 12.92;
            } else {
                return 1.055 * Math.pow(linear, 1.0 / 2.4) - 0.055;
            }
        } else {
            return Math.sqrt(linear);
        }
    }
    private double srgbByteToLinear(int srgbByte) {
        return srgbToLinear(srgbByte / 255.0);
    }
    private int linearToSrgbByte(double linear) {
        linear = Math.max(0.0, Math.min(1.0, linear));
        double srgb = linearToSrgb(linear);
        return (int) Math.round(srgb * 255.0);
    }
    private double[] srgbToLinearRGB(int r, int g, int b) {
        return new double[] {
            srgbByteToLinear(r),
            srgbByteToLinear(g),
            srgbByteToLinear(b)
        };
    }

    private double distributionGGX(double NdotH, double roughness) {
        double a = roughness * roughness;
        double a2 = a * a;
        double NdotH2 = NdotH * NdotH;
        double denom = NdotH2 * (a2 - 1.0) + 1.0;
        denom = Math.PI * denom * denom;
        return (denom < 1e-12) ? 0.0 : a2 / denom;
    }

    private double geometrySchlickGGX(double NdotV, double roughness) {
        double r = roughness + 1.0;
        double k = (r * r) / 8.0;
        double denom = NdotV * (1.0 - k) + k;
        return (denom < 1e-12) ? 0.0 : NdotV / denom;
    }

    private double geometrySmith(double NdotV, double NdotL, double roughness) {
        return geometrySchlickGGX(NdotV, roughness) * geometrySchlickGGX(NdotL, roughness);
    }

    private double fresnelSchlick(double cosTheta, double F0) {
        double oneMinusCos = 1.0 - cosTheta;
        double pow5 = oneMinusCos * oneMinusCos * oneMinusCos * oneMinusCos * oneMinusCos;
        return F0 + (1.0 - F0) * pow5;
    }

    private double[] fresnelSchlickRGB(double cosTheta, double F0r, double F0g, double F0b) {
        double oneMinusCos = 1.0 - cosTheta;
        double pow5 = oneMinusCos * oneMinusCos * oneMinusCos * oneMinusCos * oneMinusCos;
        return new double[] {
            F0r + (1.0 - F0r) * pow5,
            F0g + (1.0 - F0g) * pow5,
            F0b + (1.0 - F0b) * pow5
        };
    }

    private double[] evaluateCookTorranceSpecular(Vector3 N, Vector3 V, Vector3 L,
                                                   double roughness,
                                                   double F0r, double F0g, double F0b) {
        Vector3 H = V.add(L).normalize();
        double NdotV = Math.max(0.001, N.dot(V));
        double NdotL = Math.max(0.0, N.dot(L));
        double NdotH = Math.max(0.0, N.dot(H));
        double HdotV = Math.max(0.0, H.dot(V));

        if (NdotL <= 0.0) return new double[] { 0.0, 0.0, 0.0 };

        double D = distributionGGX(NdotH, roughness);
        double G = geometrySmith(NdotV, NdotL, roughness);
        double[] F = fresnelSchlickRGB(HdotV, F0r, F0g, F0b);

        double denom = 4.0 * NdotV * NdotL;
        if (denom < 1e-12) denom = 1e-12;
        double specMult = (D * G / denom) * NdotL;

        return new double[] { F[0] * specMult, F[1] * specMult, F[2] * specMult };
    }

    private Vector3 rotateY(Vector3 v, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new Vector3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    private int[] sampleEnvironmentColor(Vector3 dir) {
        double t = Math.max(-1.0, Math.min(1.0, dir.y));
        t = (t * 0.5) + 0.5;
        double tp = t * t * (3.0 - 2.0 * t);

        double skyR = 0.65 * (1.0 - tp) + 0.12 * tp;
        double skyG = 0.78 * (1.0 - tp) + 0.18 * tp;
        double skyB = 0.95 * (1.0 - tp) + 0.35 * tp;

        double tint = 0.5 + 0.5 * Math.tanh(dir.x * 0.8);
        double r = skyR * tp * tint + 0.12 * (1.0 - tp) * (1.0 - tint);
        double g = skyG * tp * tint + 0.10 * (1.0 - tp) * (1.0 - tint);
        double b = skyB * tp * tint + 0.08 * (1.0 - tp) * (1.0 - tint);

        return new int[] {
            (int) Math.round(Math.min(1.0, Math.max(0.0, r)) * 255.0),
            (int) Math.round(Math.min(1.0, Math.max(0.0, g)) * 255.0),
            (int) Math.round(Math.min(1.0, Math.max(0.0, b)) * 255.0)
        };
    }

    private int clampX(int x) { return Math.max(0, Math.min(width - 1, x)); }
    private int clampY(int y) { return Math.max(0, Math.min(height - 1, y)); }
    private static class ScreenVertex {
        final int x, y;
        final double z;
        final Vector3 n, t;
        final Vector2 uv;

        ScreenVertex(int x, int y, double z, Vector3 n, Vector3 t, Vector2 uv) {
            this.x = x; this.y = y; this.z = z;
            this.n = n; this.t = t; this.uv = uv;
        }
    }
}

