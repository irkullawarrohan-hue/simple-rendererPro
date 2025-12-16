package renderer;

public class MeshTest {

        public static void main(String[] args) {
            Mesh cube = Mesh.createCube(2.0);
            System.out.println("Triangle count = " + cube.triangles.size());
            System.out.println("First triangle = " + cube.triangles.get(0));
            System.out.println("First triangle normal = " + cube.triangles.get(0).faceNormal().normalize());
        }
    }

