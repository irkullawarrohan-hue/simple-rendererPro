package renderer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class ObjLoader {

    public static Mesh load(String path) {
        List<Vector3> positions = new ArrayList<>();
        List<Vector2> uvs = new ArrayList<>();
        List<Vector3> normals = new ArrayList<>();
        Mesh mesh = new Mesh();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("v ")) {
                    String[] p = line.split("\\s+");
                    if (p.length < 4) continue;
                    double x = Double.parseDouble(p[1]);
                    double y = Double.parseDouble(p[2]);
                    double z = Double.parseDouble(p[3]);
                    positions.add(new Vector3(x, y, z));
                } else if (line.startsWith("vt ")) {
                    // uv coordinate
                    String[] p = line.split("\\s+");
                    if (p.length < 3) continue;
                    double u = Double.parseDouble(p[1]);
                    double v = Double.parseDouble(p[2]);
                    uvs.add(new Vector2(u, v));
                } else if (line.startsWith("vn ")) {
                    // vertex normal
                    String[] p = line.split("\\s+");
                    if (p.length < 4) continue;
                    double nx = Double.parseDouble(p[1]);
                    double ny = Double.parseDouble(p[2]);
                    double nz = Double.parseDouble(p[3]);
                    normals.add(new Vector3(nx, ny, nz));
                } else if (line.startsWith("f ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 4) continue; // need at least f v0 v1 v2

                    List<FaceVert> faceVerts = new ArrayList<>();
                    for (int i = 1; i < parts.length; i++) {
                        String spec = parts[i];
                        FaceVert fv = parseFaceVert(spec);
                        if (fv.vIndex < 0 || fv.vIndex >= positions.size()) {
                            faceVerts.clear();
                            break;
                        }
                        faceVerts.add(fv);
                    }
                    if (faceVerts.size() < 3) continue;
                    for (int k = 1; k < faceVerts.size() - 1; k++) {
                        FaceVert a = faceVerts.get(0);
                        FaceVert b = faceVerts.get(k);
                        FaceVert c = faceVerts.get(k + 1);

                        Vector3 v0 = clonePosition(positions.get(a.vIndex));
                        Vector3 v1 = clonePosition(positions.get(b.vIndex));
                        Vector3 v2 = clonePosition(positions.get(c.vIndex));

                        Triangle t = new Triangle(v0, v1, v2);
                        t.color = 0xFFAAAAAA;

                        if (a.vtIndex != -1 && b.vtIndex != -1 && c.vtIndex != -1) {
                            if (a.vtIndex < uvs.size() && b.vtIndex < uvs.size() && c.vtIndex < uvs.size()) {
                                t.uv0 = uvs.get(a.vtIndex);
                                t.uv1 = uvs.get(b.vtIndex);
                                t.uv2 = uvs.get(c.vtIndex);
                            }
                        }
                        if (a.vnIndex != -1 && b.vnIndex != -1 && c.vnIndex != -1) {
                            if (a.vnIndex < normals.size() && b.vnIndex < normals.size() && c.vnIndex < normals.size()) {
                                t.n0 = normals.get(a.vnIndex);
                                t.n1 = normals.get(b.vnIndex);
                                t.n2 = normals.get(c.vnIndex);
                            }
                        }

                        mesh.triangles.add(t);
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("ObjLoader: failed to load " + path + " : " + ex.getMessage());
            ex.printStackTrace();
        }
        boolean haveAnyNormals = false;
        for (Triangle t : mesh.triangles) {
            if (t.n0 != null && t.n1 != null && t.n2 != null) { haveAnyNormals = true; break; }
        }
        if (!haveAnyNormals) {
            Mesh.computeVertexNormals(mesh);
        }
        Mesh.computeVertexTangents(mesh);

        return mesh;
    }
    private static Vector3 clonePosition(Vector3 v) {
        return new Vector3(v.x, v.y, v.z);
    }
    private static FaceVert parseFaceVert(String spec) {
        FaceVert fv = new FaceVert();
        fv.vIndex = -1; fv.vtIndex = -1; fv.vnIndex = -1;
        String[] parts = spec.split("/");
        try {
            if (parts.length >= 1 && parts[0].length() > 0) {
                fv.vIndex = Integer.parseInt(parts[0]) - 1;
            }
            if (parts.length >= 2 && parts[1].length() > 0) {
                fv.vtIndex = Integer.parseInt(parts[1]) - 1;
            }
            if (parts.length >= 3 && parts[2].length() > 0) {
                fv.vnIndex = Integer.parseInt(parts[2]) - 1;
            }
        } catch (NumberFormatException e) {
            fv.vIndex = -1;
            fv.vtIndex = -1;
            fv.vnIndex = -1;
        }
        return fv;
    }
    private static class FaceVert {
        int vIndex;
        int vtIndex;
        int vnIndex;
    }
}
