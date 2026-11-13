# Local OpenGL runtime

LWJGL 3.3.6 core, GLFW, OpenGL and Assimp JARs and Linux x86_64 natives are originally copied from the former meadow-forest demo. No dependency on that app at build time or runtime; no Unity installation is required.

The common app packager embeds these dependencies in rift-arena.apk. LWJGL extracts native resources at runtime. SHA256SUMS pins the local JARs; verify with `sha256sum -c SHA256SUMS` in this directory. License notices are packaged under res/third-party/.

Requires Linux x86_64, Java 8+, X11 and OpenGL 2.1 with framebuffer objects. Other platforms need their matching native libraries.
