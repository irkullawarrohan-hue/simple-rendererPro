package renderer;

public class RendererTest {

        public static void main(String[] args) {
            Vector3 a = new Vector3(0, 0, 0);
            Vector3 b = new Vector3(1, 0, 0);
            Vector3 c = new Vector3(0, 1, 0);

            Triangle t = new Triangle(a, b, c);

            System.out.println("Triangle vertices:");
            System.out.println("v0 = " + t.v0);
            System.out.println("v1 = " + t.v1);
            System.out.println("v2 = " + t.v2);

            Vector3 normal = t.faceNormal();
            System.out.println("Face normal (unnormalized) = " + normal);
            System.out.println("Face normal (normalized) = " + normal.normalize());
        }
    }

