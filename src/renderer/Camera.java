package renderer;

public class Camera {
    public Vector3 position;
    public Vector3 forward;
    public Vector3 right;
    public Vector3 up;
    public double aspect;


    public double fov;
    public double near;
    public double far;

    public Camera(Vector3 position, Vector3 target) {
        this.position = position;
        this.fov = 70.0;
        this.near = 0.1;
        this.far = 1000.0;

        lookAt(target);
    }

    public Vector3 project(Vector3 viewSpace, int screenW, int screenH) {
        double fovRad = Math.toRadians(fov);
        double scale = Math.tan(fovRad * 0.5);
        double z = Math.max(near, viewSpace.z);

        double x = (viewSpace.x / (scale * aspect * z)) * (screenW * 0.5) + (screenW * 0.5);
        double y = (-viewSpace.y / (scale * z)) * (screenH * 0.5) + (screenH * 0.5);

        return new Vector3(x, y, viewSpace.z);
    }



    public void lookAt(Vector3 target) {
        Vector3 dir = target.sub(position);
        double len = dir.length();
        if (len < 1e-9) {
            forward = new Vector3(0, 0, 1);
        } else {
            forward = dir.scale(1.0 / len);
        }

        Vector3 worldUp = new Vector3(0, 1, 0);
        if (Math.abs(forward.dot(worldUp)) > 0.9999) {
            worldUp = new Vector3(0, 0, forward.y > 0 ? -1 : 1);
        }

        Vector3 r = forward.cross(worldUp);
        double rLen = r.length();
        right = (rLen < 1e-9) ? new Vector3(1, 0, 0) : r.scale(1.0 / rLen);

        Vector3 u = right.cross(forward);
        double uLen = u.length();
        up = (uLen < 1e-9) ? new Vector3(0, 1, 0) : u.scale(1.0 / uLen);
    }

    public Vector3 worldToView(Vector3 p) {
        Vector3 v = p.sub(position);
        return new Vector3(
                v.dot(right),
                v.dot(up),
                v.dot(forward)
        );
    }
}
