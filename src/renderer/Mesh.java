package renderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mesh {
    public List<Triangle> triangles = new ArrayList<>();

    public Mesh() {}

    public static Mesh createCube(double size) {
        Mesh m = new Mesh();
        double s = size / 2.0;
        Vector2 uv00 = new Vector2(0, 0);
        Vector2 uv10 = new Vector2(1, 0);
        Vector2 uv01 = new Vector2(0, 1);
        Vector2 uv11 = new Vector2(1, 1);
        Triangle t0 = new Triangle(new Vector3(s, -s, s), new Vector3(-s, -s, s), new Vector3(-s, s, s));
        Triangle t1 = new Triangle(new Vector3(s, -s, s), new Vector3(-s, s, s), new Vector3(s, s, s));
        t0.uv0 = uv10; t0.uv1 = uv00; t0.uv2 = uv01;
        t1.uv0 = uv10; t1.uv1 = uv01; t1.uv2 = uv11;
        m.triangles.add(t0);
        m.triangles.add(t1);
        Triangle t2 = new Triangle(new Vector3(-s, -s, -s), new Vector3(s, -s, -s), new Vector3(s, s, -s));
        Triangle t3 = new Triangle(new Vector3(-s, -s, -s), new Vector3(s, s, -s), new Vector3(-s, s, -s));
        t2.uv0 = uv00; t2.uv1 = uv10; t2.uv2 = uv11;
        t3.uv0 = uv00; t3.uv1 = uv11; t3.uv2 = uv01;
        m.triangles.add(t2);
        m.triangles.add(t3);

        // Left face
        Triangle t4 = new Triangle(new Vector3(-s, -s, s), new Vector3(-s, -s, -s), new Vector3(-s, s, -s));
        Triangle t5 = new Triangle(new Vector3(-s, -s, s), new Vector3(-s, s, -s), new Vector3(-s, s, s));
        t4.uv0 = uv10; t4.uv1 = uv00; t4.uv2 = uv01;
        t5.uv0 = uv10; t5.uv1 = uv01; t5.uv2 = uv11;
        m.triangles.add(t4);
        m.triangles.add(t5);

        // Right face
        Triangle t6 = new Triangle(new Vector3(s, -s, -s), new Vector3(s, -s, s), new Vector3(s, s, s));
        Triangle t7 = new Triangle(new Vector3(s, -s, -s), new Vector3(s, s, s), new Vector3(s, s, -s));
        t6.uv0 = uv00; t6.uv1 = uv10; t6.uv2 = uv11;
        t7.uv0 = uv00; t7.uv1 = uv11; t7.uv2 = uv01;
        m.triangles.add(t6);
        m.triangles.add(t7);

        // Top face
        Triangle t8 = new Triangle(new Vector3(-s, s, s), new Vector3(-s, s, -s), new Vector3(s, s, -s));
        Triangle t9 = new Triangle(new Vector3(-s, s, s), new Vector3(s, s, -s), new Vector3(s, s, s));
        t8.uv0 = uv00; t8.uv1 = uv01; t8.uv2 = uv11;
        t9.uv0 = uv00; t9.uv1 = uv11; t9.uv2 = uv10;
        m.triangles.add(t8);
        m.triangles.add(t9);

        // Bottom face (y = -s)
        Triangle t10 = new Triangle(new Vector3(-s, -s, -s), new Vector3(-s, -s, s), new Vector3(s, -s, s));
        Triangle t11 = new Triangle(new Vector3(-s, -s, -s), new Vector3(s, -s, s), new Vector3(s, -s, -s));
        t10.uv0 = uv00; t10.uv1 = uv01; t10.uv2 = uv11;
        t11.uv0 = uv00; t11.uv1 = uv11; t11.uv2 = uv10;
        m.triangles.add(t10);
        m.triangles.add(t11);
        int[] colors = {
                0xFFFF6B6B,
                0xFF6BCB77,
                0xFF4D96FF,
                0xFFFFD93D,
                0xFFB66BFF,
                0xFF56D9D9
        };
        for (int i = 0; i < m.triangles.size(); i++) {
            m.triangles.get(i).color = colors[(i / 2) % colors.length];
        }
        computeVertexNormals(m);
        computeVertexTangents(m);

        return m;
    }

    public static void computeVertexNormals(Mesh m) {
        Map<Vector3, Vector3> accum = new HashMap<>();
        for (Triangle t : m.triangles) {
            Vector3 fn = t.faceNormal();
            accum.putIfAbsent(t.v0, new Vector3(0,0,0));
            accum.putIfAbsent(t.v1, new Vector3(0,0,0));
            accum.putIfAbsent(t.v2, new Vector3(0,0,0));

            accum.put(t.v0, accum.get(t.v0).add(fn));
            accum.put(t.v1, accum.get(t.v1).add(fn));
            accum.put(t.v2, accum.get(t.v2).add(fn));
        }

        for (Triangle t : m.triangles) {
            t.n0 = accum.get(t.v0).normalize();
            t.n1 = accum.get(t.v1).normalize();
            t.n2 = accum.get(t.v2).normalize();
        }
    }
    public static void computeVertexTangents(Mesh m) {
        Map<Vector3, Vector3> tangentAccum = new HashMap<>();

        for (Triangle t : m.triangles) {
            if (t.uv0 == null || t.uv1 == null || t.uv2 == null) {
                continue;
            }
            Vector3 edge1 = t.v1.sub(t.v0);
            Vector3 edge2 = t.v2.sub(t.v0);
            double du1 = t.uv1.u - t.uv0.u;
            double dv1 = t.uv1.v - t.uv0.v;
            double du2 = t.uv2.u - t.uv0.u;
            double dv2 = t.uv2.v - t.uv0.v;

            double det = du1 * dv2 - du2 * dv1;
            if (Math.abs(det) < 1e-9) {
                continue;
            }

            double invDet = 1.0 / det;
            Vector3 tangent = edge1.scale(dv2).sub(edge2.scale(dv1)).scale(invDet);

            tangentAccum.putIfAbsent(t.v0, new Vector3(0, 0, 0));
            tangentAccum.putIfAbsent(t.v1, new Vector3(0, 0, 0));
            tangentAccum.putIfAbsent(t.v2, new Vector3(0, 0, 0));

            tangentAccum.put(t.v0, tangentAccum.get(t.v0).add(tangent));
            tangentAccum.put(t.v1, tangentAccum.get(t.v1).add(tangent));
            tangentAccum.put(t.v2, tangentAccum.get(t.v2).add(tangent));
        }
        for (Triangle t : m.triangles) {
            t.t0 = computeOrthonormalTangent(t.n0, tangentAccum.get(t.v0));
            t.t1 = computeOrthonormalTangent(t.n1, tangentAccum.get(t.v1));
            t.t2 = computeOrthonormalTangent(t.n2, tangentAccum.get(t.v2));
        }
    }
    private static Vector3 computeOrthonormalTangent(Vector3 normal, Vector3 tangent) {
        if (tangent == null || tangent.length() < 1e-9) {
            return generateFallbackTangent(normal);
        }
        if (normal == null || normal.length() < 1e-9) {
            return tangent.normalize();
        }
        double dot = tangent.dot(normal);
        Vector3 orthogonal = tangent.sub(normal.scale(dot));
        if (orthogonal.length() < 1e-9) {
            return generateFallbackTangent(normal);
        }

        return orthogonal.normalize();
    }
    private static Vector3 generateFallbackTangent(Vector3 normal) {
        if (normal == null || normal.length() < 1e-9) {
            return new Vector3(1, 0, 0);
        }
        Vector3 ref;
        if (Math.abs(normal.y) < 0.9) {
            ref = new Vector3(0, 1, 0);
        } else {
            ref = new Vector3(1, 0, 0);
        }
        Vector3 tangent = normal.cross(ref);
        if (tangent.length() < 1e-9) {
            tangent = normal.cross(new Vector3(0, 0, 1));
        }

        return tangent.normalize();
    }
}
