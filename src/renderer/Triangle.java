package renderer;

public class Triangle {
    public Vector3 v0, v1, v2;
    public Vector3 n0, n1, n2;
    public Vector2 uv0, uv1, uv2;
    public Vector3 t0, t1, t2;

    public int color;

    public Triangle(Vector3 v0, Vector3 v1, Vector3 v2) {
        this.v0 = v0; this.v1 = v1; this.v2 = v2;
        this.n0 = this.n1 = this.n2 = null;
        this.uv0 = this.uv1 = this.uv2 = null;
        this.t0 = this.t1 = this.t2 = null;
        this.color = 0xFF808080;
    }

    public Vector3 faceNormal() {
        Vector3 a = v1.sub(v0);
        Vector3 b = v2.sub(v0);
        return a.cross(b);
    }
}
