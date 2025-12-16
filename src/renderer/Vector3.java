package renderer;

public class Vector3 {
    public double x, y, z;

    public Vector3() { this(0,0,0); }
    public Vector3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }

    public Vector3 add(Vector3 o) { return new Vector3(x + o.x, y + o.y, z + o.z); }
    public Vector3 sub(Vector3 o) { return new Vector3(x - o.x, y - o.y, z - o.z); }
    public Vector3 scale(double s) { return new Vector3(x * s, y * s, z * s); }

    public double dot(Vector3 o) { return x * o.x + y * o.y + z * o.z; }

    public Vector3 cross(Vector3 o) {
        return new Vector3(
                y * o.z - z * o.y,
                z * o.x - x * o.z,
                x * o.y - y * o.x
        );
    }

    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    public Vector3 normalize() {
        double len = length();
        if (len < 1e-12) return new Vector3(0, 0, 0);
        return scale(1.0 / len);
    }

    @Override
    public String toString() {
        return String.format("Vector3(%.4f, %.4f, %.4f)", x, y, z);
    }
}
