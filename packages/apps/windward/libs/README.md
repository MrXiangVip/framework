# Local OpenGL runtime

Independent copies of the repository's LWJGL 3.3.6 core, GLFW, OpenGL and Assimp jars and Linux x86_64 native jars, sourced from packages/apps/rift-arena/libs. Assimp imports the selected forest meshes and Fantasy character skeleton directly from APK resources.

The common application packager merges these into windward.apk; runtime does not depend on the original application. Verify local jars with `sha256sum -c SHA256SUMS`. License notices are in res/third-party/ and included in the APK.
