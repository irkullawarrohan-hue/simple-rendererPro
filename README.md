SimpleRenderer Pro

SimpleRenderer Pro is a CPU based 3D software renderer written in Java.
It does not use OpenGL, DirectX, Vulkan, GPU shaders, or any graphics API.
Every stage of rendering, from transforming vertices to producing final pixels on screen, runs entirely on the CPU.

This project was built to deeply understand how a real 3D rendering pipeline works internally, without relying on hardware acceleration or external abstractions.

Project overview

Most modern graphics applications sit on top of large graphics APIs that hide the core rendering logic behind drivers and shaders. While this is practical, it also makes it easy to use a renderer without understanding what is actually happening underneath.

SimpleRenderer Pro takes the opposite approach.

It is a from scratch implementation of a real time 3D rendering pipeline. The focus is on correctness, clarity, and system design rather than visual tricks or shortcuts. The project is intended as a technical tool and learning system, not as a production game engine.

What makes this project different

The entire rendering pipeline is implemented manually on the CPU
No external rendering libraries or GPU bindings are used
Physically based rendering is implemented fully in software
The UI is designed as an inspection and debugging tool, not a game interface
The codebase is structured as a system with clear responsibilities

Core features

Rendering pipeline
The renderer implements a complete model to world to view to projection pipeline.
Triangles are rasterized using barycentric interpolation with a depth buffer for correct visibility.
Backface culling and frustum culling are used to reject unnecessary geometry early.
OBJ meshes with UV coordinates are supported.
Textures are sampled using bilinear filtering.

Lighting and materials
Lighting is based on a physically based rendering model using the Cook Torrance BRDF.
GGX microfacet distribution is used for specular highlights.
Materials follow a metalness and roughness workflow.
Multiple built in material presets are available for comparison.
Normal mapping is supported using tangent space.
Optional image based lighting can be enabled using HDR environment maps.

Post processing
Screen space ambient occlusion is implemented to improve depth perception.
Bloom is applied using a multi pass Gaussian blur.
Vignette and exposure controls are available.
Tone mapping is applied before final display.

UI and tooling
The application uses an inspector style UI similar to professional 3D tools.
Rendering and material parameters can be adjusted in real time.
The viewport is the primary focus, with orbit, pan, and zoom camera controls.
Debug overlays show frame rate, triangle count, and culling statistics.
Rendering debug modes are available for inspection.

Performance
Rasterization is performed using a tile based approach.
Rendering work is distributed across multiple CPU threads.
Output is deterministic with no race condition artifacts.
Per frame statistics are collected for performance inspection.

Rendering pipeline overview

The renderer follows a traditional forward rendering pipeline implemented entirely in software.

Vertices are first transformed from object space to world space and then into view space using the camera transform.
View space coordinates are projected using a perspective projection matrix.
Backface culling removes triangles facing away from the camera, and frustum culling skips geometry outside the view volume.
Triangles are rasterized into fragments using barycentric coordinates to interpolate depth, normals, UVs, and tangents.
Each fragment is tested against the depth buffer so only the nearest surface contributes to the final image.
Lighting is evaluated per fragment using a physically based shading model.
The image then passes through post processing stages before being displayed.

Architecture and design decisions

The renderer is CPU based by design. Writing a renderer on the CPU forces every stage of the pipeline to be explicit and visible, which makes it ideal for learning and experimentation.

No external graphics APIs are used because they would remove the need to implement core stages manually. The only dependencies are Java’s standard library and Swing for windowing.

Swing is used for the UI because it is stable, available in every JDK, and sufficient for a tool style interface with panels, sliders, and debug views. This project is an engineering tool, not a consumer application.

Readability and structure are prioritized over extreme performance optimizations. Feature scope is intentionally limited to keep the system understandable and maintainable.

Performance considerations

The renderer performs early rejection through backface and frustum culling.
Depth buffering is used to avoid unnecessary overdraw.
Rasterization is parallelized across CPU cores using a tile based approach.
Synchronization between threads is kept minimal.

Shading is the main performance cost because physically based lighting is expensive on the CPU.
There is no deferred shading pipeline, no SIMD acceleration, and some per frame allocations remain.
At moderate resolutions and mesh complexity, the renderer achieves interactive frame rates suitable for inspection and experimentation.

Limitations

This project intentionally does not include GPU acceleration, shadow mapping, skeletal animation, transparency, anti aliasing, mipmapping, or advanced asset formats. These are known limitations and design decisions, not unfinished work.

Possible future improvements

A GPU backend could be added for comparison with CPU rendering.
Deferred shading could improve lighting performance.
Shadow mapping could be introduced for dynamic lighting.
A scene graph or ECS architecture could improve scene organization.
More advanced image based lighting techniques could be implemented.
The asset pipeline could be expanded to support modern formats.

Relevance for backend and systems engineering

Although this is a graphics project, the engineering principles apply directly to backend and systems development.

Rendering pipelines resemble request processing pipelines.
Early culling mirrors query optimization and short circuit evaluation.
Depth buffering relates to ordering and conflict resolution.
Tile based parallelism maps to worker pools and sharded processing.
Profiling tools reflect real world observability and performance analysis.
Memory and allocation awareness translates directly to GC sensitive backend services.

Building a renderer from scratch demonstrates strong systems thinking, performance awareness, and architectural discipline.

How to run

This project requires Java 17 or newer.
No external dependencies are needed.

Compile the source files and run the main entry point.
