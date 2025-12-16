package renderer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class ResourceManager {
    private static ResourceManager instance;
    private final Map<String, CachedTexture> textureCache;
    private final Map<String, CachedMesh> meshCache;
    private final Map<String, CachedEnvironmentMap> envMapCache;
    private long totalTextureMemory = 0;
    private long totalMeshMemory = 0;
    private long totalEnvMapMemory = 0;
    private int textureLoadCount = 0;
    private int textureCacheHits = 0;
    private int meshLoadCount = 0;
    private int meshCacheHits = 0;
    private static class CachedTexture {
        final Texture texture;
        final String path;
        final long memoryBytes;
        int refCount;

        CachedTexture(Texture texture, String path) {
            this.texture = texture;
            this.path = path;
            this.refCount = 1;
            this.memoryBytes = (long) texture.getWidth() * texture.getHeight() * 4;
        }
    }
    private static class CachedMesh {
        final Mesh mesh;
        final String path;
        final long memoryBytes;
        int refCount;

        CachedMesh(Mesh mesh, String path) {
            this.mesh = mesh;
            this.path = path;
            this.refCount = 1;
            this.memoryBytes = (long) mesh.triangles.size() * 268;
        }
    }
    private static class CachedEnvironmentMap {
        final EnvironmentMap envMap;
        final String path;
        final long memoryBytes;
        int refCount;

        CachedEnvironmentMap(EnvironmentMap envMap, String path) {
            this.envMap = envMap;
            this.path = path;
            this.refCount = 1;
            this.memoryBytes = (long) envMap.getWidth() * envMap.getHeight() * 24
                             + 32L * 64 * 24;
        }
    }
    private ResourceManager() {
        textureCache = new ConcurrentHashMap<>();
        meshCache = new ConcurrentHashMap<>();
        envMapCache = new ConcurrentHashMap<>();
    }
    public static synchronized ResourceManager getInstance() {
        if (instance == null) {
            instance = new ResourceManager();
        }
        return instance;
    }
    public Texture loadTexture(String path) {
        String key = normalizePath(path);
        CachedTexture cached = textureCache.get(key);
        if (cached != null) {
            cached.refCount++;
            textureCacheHits++;
            return cached.texture;
        }
        try {
            Texture texture = new Texture(path);
            CachedTexture entry = new CachedTexture(texture, path);
            textureCache.put(key, entry);
            totalTextureMemory += entry.memoryBytes;
            textureLoadCount++;

            System.out.println("ResourceManager: Loaded texture '" + path + "' (" +
                              formatBytes(entry.memoryBytes) + ")");
            return texture;

        } catch (Exception e) {
            System.err.println("ResourceManager: Failed to load texture '" + path + "': " + e.getMessage());
            return null;
        }
    }
    public void releaseTexture(String path) {
        String key = normalizePath(path);
        CachedTexture cached = textureCache.get(key);

        if (cached != null) {
            cached.refCount--;
            if (cached.refCount <= 0) {
                textureCache.remove(key);
                totalTextureMemory -= cached.memoryBytes;
                System.out.println("ResourceManager: Unloaded texture '" + path + "'");
            }
        }
    }
    public boolean isTextureCached(String path) {
        return textureCache.containsKey(normalizePath(path));
    }
    public Mesh loadMesh(String path) {
        String key = normalizePath(path);
        CachedMesh cached = meshCache.get(key);
        if (cached != null) {
            cached.refCount++;
            meshCacheHits++;
            return cached.mesh;
        }
        try {
            Mesh mesh = ObjLoader.load(path);
            if (mesh == null) {
                System.err.println("ResourceManager: Failed to load mesh '" + path + "'");
                return null;
            }

            CachedMesh entry = new CachedMesh(mesh, path);
            meshCache.put(key, entry);
            totalMeshMemory += entry.memoryBytes;
            meshLoadCount++;

            System.out.println("ResourceManager: Loaded mesh '" + path + "' (" +
                              mesh.triangles.size() + " tris, " +
                              formatBytes(entry.memoryBytes) + ")");
            return mesh;

        } catch (Exception e) {
            System.err.println("ResourceManager: Failed to load mesh '" + path + "': " + e.getMessage());
            return null;
        }
    }
    public void releaseMesh(String path) {
        String key = normalizePath(path);
        CachedMesh cached = meshCache.get(key);

        if (cached != null) {
            cached.refCount--;
            if (cached.refCount <= 0) {
                meshCache.remove(key);
                totalMeshMemory -= cached.memoryBytes;
                System.out.println("ResourceManager: Unloaded mesh '" + path + "'");
            }
        }
    }
    public boolean isMeshCached(String path) {
        return meshCache.containsKey(normalizePath(path));
    }
    public EnvironmentMap loadEnvironmentMap(String path) {
        String key = normalizePath(path);
        CachedEnvironmentMap cached = envMapCache.get(key);
        if (cached != null) {
            cached.refCount++;
            return cached.envMap;
        }
        EnvironmentMap envMap = new EnvironmentMap();
        if (!envMap.load(path)) {
            System.err.println("ResourceManager: Failed to load environment map '" + path + "'");
            return null;
        }

        CachedEnvironmentMap entry = new CachedEnvironmentMap(envMap, path);
        envMapCache.put(key, entry);
        totalEnvMapMemory += entry.memoryBytes;

        System.out.println("ResourceManager: Loaded environment map '" + path + "' (" +
                          envMap.getWidth() + "x" + envMap.getHeight() + ", " +
                          formatBytes(entry.memoryBytes) + ")");
        return envMap;
    }
    public void releaseEnvironmentMap(String path) {
        String key = normalizePath(path);
        CachedEnvironmentMap cached = envMapCache.get(key);

        if (cached != null) {
            cached.refCount--;
            if (cached.refCount <= 0) {
                envMapCache.remove(key);
                totalEnvMapMemory -= cached.memoryBytes;
                System.out.println("ResourceManager: Unloaded environment map '" + path + "'");
            }
        }
    }
    public long getTotalMemoryUsage() {
        return totalTextureMemory + totalMeshMemory + totalEnvMapMemory;
    }
    public String getMemoryReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Resource Manager Memory Report ===\n");
        sb.append(String.format("Textures:    %d cached, %s\n",
                 textureCache.size(), formatBytes(totalTextureMemory)));
        sb.append(String.format("Meshes:      %d cached, %s\n",
                 meshCache.size(), formatBytes(totalMeshMemory)));
        sb.append(String.format("Env Maps:    %d cached, %s\n",
                 envMapCache.size(), formatBytes(totalEnvMapMemory)));
        sb.append(String.format("TOTAL:       %s\n", formatBytes(getTotalMemoryUsage())));
        sb.append(String.format("\nCache Stats:\n"));
        sb.append(String.format("  Texture loads: %d, cache hits: %d (%.1f%%)\n",
                 textureLoadCount, textureCacheHits,
                 textureLoadCount > 0 ? 100.0 * textureCacheHits / (textureLoadCount + textureCacheHits) : 0));
        sb.append(String.format("  Mesh loads: %d, cache hits: %d (%.1f%%)\n",
                 meshLoadCount, meshCacheHits,
                 meshLoadCount > 0 ? 100.0 * meshCacheHits / (meshLoadCount + meshCacheHits) : 0));
        return sb.toString();
    }
    public String getMemorySummary() {
        return String.format("Resources: %d tex (%s), %d mesh (%s), %d env (%s) = %s total",
                textureCache.size(), formatBytes(totalTextureMemory),
                meshCache.size(), formatBytes(totalMeshMemory),
                envMapCache.size(), formatBytes(totalEnvMapMemory),
                formatBytes(getTotalMemoryUsage()));
    }
    public void unloadAll() {
        int texCount = textureCache.size();
        int meshCount = meshCache.size();
        int envCount = envMapCache.size();

        textureCache.clear();
        meshCache.clear();
        envMapCache.clear();

        totalTextureMemory = 0;
        totalMeshMemory = 0;
        totalEnvMapMemory = 0;

        System.out.println("ResourceManager: Unloaded all resources (" +
                          texCount + " textures, " + meshCount + " meshes, " +
                          envCount + " env maps)");
    }
    public void collectGarbage() {
        int collected = 0;

        // Collect unused textures
        var texIterator = textureCache.entrySet().iterator();
        while (texIterator.hasNext()) {
            var entry = texIterator.next();
            if (entry.getValue().refCount <= 0) {
                totalTextureMemory -= entry.getValue().memoryBytes;
                texIterator.remove();
                collected++;
            }
        }
        var meshIterator = meshCache.entrySet().iterator();
        while (meshIterator.hasNext()) {
            var entry = meshIterator.next();
            if (entry.getValue().refCount <= 0) {
                totalMeshMemory -= entry.getValue().memoryBytes;
                meshIterator.remove();
                collected++;
            }
        }
        var envIterator = envMapCache.entrySet().iterator();
        while (envIterator.hasNext()) {
            var entry = envIterator.next();
            if (entry.getValue().refCount <= 0) {
                totalEnvMapMemory -= entry.getValue().memoryBytes;
                envIterator.remove();
                collected++;
            }
        }

        if (collected > 0) {
            System.out.println("ResourceManager: Garbage collected " + collected + " resources");
        }
    }
    private String normalizePath(String path) {

        return path.replace('\\', '/').toLowerCase();
    }
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    public void listResources() {
        System.out.println("=== Cached Textures ===");
        for (var entry : textureCache.entrySet()) {
            System.out.println("  " + entry.getKey() + " (refs: " + entry.getValue().refCount +
                             ", " + formatBytes(entry.getValue().memoryBytes) + ")");
        }

        System.out.println("=== Cached Meshes ===");
        for (var entry : meshCache.entrySet()) {
            System.out.println("  " + entry.getKey() + " (refs: " + entry.getValue().refCount +
                             ", " + entry.getValue().mesh.triangles.size() + " tris, " +
                             formatBytes(entry.getValue().memoryBytes) + ")");
        }

        System.out.println("=== Cached Environment Maps ===");
        for (var entry : envMapCache.entrySet()) {
            System.out.println("  " + entry.getKey() + " (refs: " + entry.getValue().refCount +
                             ", " + formatBytes(entry.getValue().memoryBytes) + ")");
        }
    }
}

