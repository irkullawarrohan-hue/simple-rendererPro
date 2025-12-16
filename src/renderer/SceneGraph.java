package renderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class SceneGraph {
    private final SceneNode root;
    private final Map<String, SceneNode> nodesByName;
    private int totalNodes = 0;
    private int visibleNodes = 0;

    public SceneGraph() {
        this.root = new SceneNode("root");
        this.nodesByName = new HashMap<>();
        nodesByName.put("root", root);
    }
    public static class SceneNode {
        private final String name;
        private final String id;
        private static int nextId = 0;
        private SceneNode parent;
        private final List<SceneNode> children;
        private Vector3 position;
        private Vector3 rotation;
        private Vector3 scale;
        private double[] worldMatrix;
        private boolean worldMatrixDirty = true;
        private Mesh mesh;
        private Material material;
        private boolean visible = true;
        private boolean castsShadows = true;
        private boolean receivesShadows = true;
        private BoundingBox localBounds;
        private BoundingBox worldBounds;

        public SceneNode(String name) {
            this.name = name;
            this.id = "node_" + (nextId++);
            this.children = new ArrayList<>();
            this.position = new Vector3(0, 0, 0);
            this.rotation = new Vector3(0, 0, 0);
            this.scale = new Vector3(1, 1, 1);
            this.worldMatrix = new double[16];
            setIdentityMatrix(worldMatrix);
        }

        public void addChild(SceneNode child) {
            if (child.parent != null) {
                child.parent.children.remove(child);
            }
            child.parent = this;
            children.add(child);
            child.markWorldMatrixDirty();
        }

        public void removeChild(SceneNode child) {
            if (children.remove(child)) {
                child.parent = null;
            }
        }

        public SceneNode getParent() { return parent; }
        public List<SceneNode> getChildren() { return children; }
        public String getName() { return name; }
        public String getId() { return id; }

        public void setPosition(double x, double y, double z) {
            this.position = new Vector3(x, y, z);
            markWorldMatrixDirty();
        }

        public void setPosition(Vector3 pos) {
            this.position = pos;
            markWorldMatrixDirty();
        }

        public Vector3 getPosition() { return position; }

        public void setRotation(double rx, double ry, double rz) {
            this.rotation = new Vector3(rx, ry, rz);
            markWorldMatrixDirty();
        }

        public void setRotationDegrees(double rx, double ry, double rz) {
            setRotation(Math.toRadians(rx), Math.toRadians(ry), Math.toRadians(rz));
        }

        public Vector3 getRotation() { return rotation; }

        public void setScale(double x, double y, double z) {
            this.scale = new Vector3(x, y, z);
            markWorldMatrixDirty();
        }

        public void setUniformScale(double s) {
            setScale(s, s, s);
        }

        public Vector3 getScale() { return scale; }
        private void markWorldMatrixDirty() {
            worldMatrixDirty = true;
            for (SceneNode child : children) {
                child.markWorldMatrixDirty();
            }
        }
        public double[] getWorldMatrix() {
            if (worldMatrixDirty) {
                computeWorldMatrix();
            }
            return worldMatrix;
        }
        private void computeWorldMatrix() {
            double[] localMatrix = new double[16];
            computeLocalMatrix(localMatrix);

            if (parent != null) {
                double[] parentWorld = parent.getWorldMatrix();
                multiplyMatrix(parentWorld, localMatrix, worldMatrix);
            } else {
                System.arraycopy(localMatrix, 0, worldMatrix, 0, 16);
            }

            worldMatrixDirty = false;
            updateWorldBounds();
        }
        private void computeLocalMatrix(double[] out) {
            double[] S = new double[16];
            setIdentityMatrix(S);
            S[0] = scale.x;
            S[5] = scale.y;
            S[10] = scale.z;
            double[] R = computeRotationMatrix(rotation.x, rotation.y, rotation.z);

            // Translation matrix
            double[] T = new double[16];
            setIdentityMatrix(T);
            T[12] = position.x;
            T[13] = position.y;
            T[14] = position.z;
            double[] RS = new double[16];
            multiplyMatrix(R, S, RS);
            multiplyMatrix(T, RS, out);
        }
        public Vector3 localToWorld(Vector3 local) {
            double[] m = getWorldMatrix();
            double x = local.x * m[0] + local.y * m[4] + local.z * m[8] + m[12];
            double y = local.x * m[1] + local.y * m[5] + local.z * m[9] + m[13];
            double z = local.x * m[2] + local.y * m[6] + local.z * m[10] + m[14];
            return new Vector3(x, y, z);
        }
        public Vector3 localToWorldDirection(Vector3 dir) {
            double[] m = getWorldMatrix();
            double x = dir.x * m[0] + dir.y * m[4] + dir.z * m[8];
            double y = dir.x * m[1] + dir.y * m[5] + dir.z * m[9];
            double z = dir.x * m[2] + dir.y * m[6] + dir.z * m[10];
            return new Vector3(x, y, z).normalize();
        }

        public void setMesh(Mesh mesh) {
            this.mesh = mesh;
            if (mesh != null) {
                computeLocalBounds();
            }
        }

        public Mesh getMesh() { return mesh; }

        public void setMaterial(Material material) {
            this.material = material;
        }

        public Material getMaterial() { return material; }

        public void setVisible(boolean visible) { this.visible = visible; }
        public boolean isVisible() { return visible; }

        public void setCastsShadows(boolean casts) { this.castsShadows = casts; }
        public boolean castsShadows() { return castsShadows; }

        public void setReceivesShadows(boolean receives) { this.receivesShadows = receives; }
        public boolean receivesShadows() { return receivesShadows; }

        private void computeLocalBounds() {
            if (mesh == null || mesh.triangles.isEmpty()) {
                localBounds = new BoundingBox(new Vector3(0,0,0), new Vector3(0,0,0));
                return;
            }

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

            for (Triangle t : mesh.triangles) {
                for (Vector3 v : new Vector3[]{t.v0, t.v1, t.v2}) {
                    minX = Math.min(minX, v.x);
                    minY = Math.min(minY, v.y);
                    minZ = Math.min(minZ, v.z);
                    maxX = Math.max(maxX, v.x);
                    maxY = Math.max(maxY, v.y);
                    maxZ = Math.max(maxZ, v.z);
                }
            }

            localBounds = new BoundingBox(
                new Vector3(minX, minY, minZ),
                new Vector3(maxX, maxY, maxZ)
            );
        }

        private void updateWorldBounds() {
            if (localBounds == null) {
                worldBounds = null;
                return;
            }
            Vector3[] corners = localBounds.getCorners();

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

            for (Vector3 corner : corners) {
                Vector3 worldCorner = localToWorld(corner);
                minX = Math.min(minX, worldCorner.x);
                minY = Math.min(minY, worldCorner.y);
                minZ = Math.min(minZ, worldCorner.z);
                maxX = Math.max(maxX, worldCorner.x);
                maxY = Math.max(maxY, worldCorner.y);
                maxZ = Math.max(maxZ, worldCorner.z);
            }

            worldBounds = new BoundingBox(
                new Vector3(minX, minY, minZ),
                new Vector3(maxX, maxY, maxZ)
            );
        }

        public BoundingBox getWorldBounds() { return worldBounds; }
        public BoundingBox getLocalBounds() { return localBounds; }
    }
    public static class BoundingBox {
        public final Vector3 min;
        public final Vector3 max;

        public BoundingBox(Vector3 min, Vector3 max) {
            this.min = min;
            this.max = max;
        }

        public Vector3 getCenter() {
            return new Vector3(
                (min.x + max.x) * 0.5,
                (min.y + max.y) * 0.5,
                (min.z + max.z) * 0.5
            );
        }

        public Vector3 getSize() {
            return new Vector3(
                max.x - min.x,
                max.y - min.y,
                max.z - min.z
            );
        }

        public Vector3[] getCorners() {
            return new Vector3[] {
                new Vector3(min.x, min.y, min.z),
                new Vector3(max.x, min.y, min.z),
                new Vector3(min.x, max.y, min.z),
                new Vector3(max.x, max.y, min.z),
                new Vector3(min.x, min.y, max.z),
                new Vector3(max.x, min.y, max.z),
                new Vector3(min.x, max.y, max.z),
                new Vector3(max.x, max.y, max.z)
            };
        }
        public boolean intersectsFrustum(Camera camera) {
            Vector3 center = getCenter();
            Vector3 size = getSize();
            double radius = Math.sqrt(size.x*size.x + size.y*size.y + size.z*size.z) * 0.5;
            Vector3 viewCenter = camera.worldToView(center);
            if (viewCenter.z + radius < camera.near) return false;
            if (viewCenter.z - radius > camera.far) return false;

            // Simple frustum width check at center depth
            double fovRad = Math.toRadians(camera.fov);
            double halfHeight = Math.tan(fovRad * 0.5) * viewCenter.z;
            double halfWidth = halfHeight * camera.aspect;

            if (viewCenter.x - radius > halfWidth || viewCenter.x + radius < -halfWidth) return false;
            if (viewCenter.y - radius > halfHeight || viewCenter.y + radius < -halfHeight) return false;

            return true;
        }
    }
    public SceneNode getRoot() { return root; }

    public SceneNode createNode(String name) {
        SceneNode node = new SceneNode(name);
        nodesByName.put(name, node);
        totalNodes++;
        return node;
    }

    public SceneNode findNode(String name) {
        return nodesByName.get(name);
    }
    public List<RenderableInstance> collectRenderables(Camera camera) {
        List<RenderableInstance> renderables = new ArrayList<>();
        visibleNodes = 0;
        collectRenderablesRecursive(root, camera, renderables, true);
        return renderables;
    }

    private void collectRenderablesRecursive(SceneNode node, Camera camera,
                                              List<RenderableInstance> out, boolean parentVisible) {
        if (!node.isVisible()) return;
        boolean inFrustum = parentVisible;
        if (node.getWorldBounds() != null) {
            inFrustum = node.getWorldBounds().intersectsFrustum(camera);
        }

        if (!inFrustum) return;

        visibleNodes++;
        if (node.getMesh() != null) {
            out.add(new RenderableInstance(node));
        }
        for (SceneNode child : node.getChildren()) {
            collectRenderablesRecursive(child, camera, out, inFrustum);
        }
    }
    public static class RenderableInstance {
        public final SceneNode node;
        public final Mesh mesh;
        public final Material material;
        public final double[] worldMatrix;

        public RenderableInstance(SceneNode node) {
            this.node = node;
            this.mesh = node.getMesh();
            this.material = node.getMaterial();
            this.worldMatrix = node.getWorldMatrix().clone();
        }
    }

    public int getTotalNodes() { return totalNodes; }
    public int getVisibleNodes() { return visibleNodes; }

    private static void setIdentityMatrix(double[] m) {
        java.util.Arrays.fill(m, 0);
        m[0] = m[5] = m[10] = m[15] = 1.0;
    }

    private static void multiplyMatrix(double[] a, double[] b, double[] out) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                out[i * 4 + j] = 0;
                for (int k = 0; k < 4; k++) {
                    out[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j];
                }
            }
        }
    }

    private static double[] computeRotationMatrix(double rx, double ry, double rz) {
        double cx = Math.cos(rx), sx = Math.sin(rx);
        double cy = Math.cos(ry), sy = Math.sin(ry);
        double cz = Math.cos(rz), sz = Math.sin(rz);

        double[] m = new double[16];

        m[0] = cy * cz;
        m[1] = cy * sz;
        m[2] = -sy;
        m[3] = 0;

        m[4] = sx * sy * cz - cx * sz;
        m[5] = sx * sy * sz + cx * cz;
        m[6] = sx * cy;
        m[7] = 0;

        m[8] = cx * sy * cz + sx * sz;
        m[9] = cx * sy * sz - sx * cz;
        m[10] = cx * cy;
        m[11] = 0;

        m[12] = 0;
        m[13] = 0;
        m[14] = 0;
        m[15] = 1;

        return m;
    }
}

