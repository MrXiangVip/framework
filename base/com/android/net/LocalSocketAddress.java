
package com.android.net;
public class LocalSocketAddress
{
    private final String name;
    private final Namespace namespace;
    public enum Namespace {
        /** A socket in the Linux abstract namespace */
        ABSTRACT(0),
        /**
         * A socket in the Android reserved namespace in /dev/socket.
         * Only the init process may create a socket here.
         */
        RESERVED(1),
        /**
         * A socket named with a normal filesystem path.
         */
        FILESYSTEM(2);

        /** The id matches with a #define in include/cutils/sockets.h */
        private int id;
        Namespace (int id) {
            this.id = id;
        }

        /**
         * @return int constant shared with native code
         */
        /*package*/ int getId() {
            return id;
        }
    }
    /**
         * Creates an instance with a given name.
         *
         * @param name non-null name
         * @param namespace namespace the name should be created in.
    */
    public LocalSocketAddress(String name, Namespace namespace) {
            this.name = name;
            this.namespace = namespace;
    }

}