package renderer;

public class Vector2 {

        public double u, v;
        public Vector2(double u, double v) { this.u = u; this.v = v; }
        public Vector2 add(Vector2 o) { return new Vector2(u + o.u, v + o.v); }
        public Vector2 scale(double s) { return new Vector2(u * s, v * s); }
        public String toString() { return String.format("Vector2(%.4f,%.4f)", u, v); }

    }
