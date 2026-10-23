package com.packages.browser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Versioned, bounded messages on private child-process pipes; stdout is never a log. */
public final class BrowserProtocol {
    public static final int MAGIC = 0x42525731, COMMAND = 1, FRAME = 2, STATE = 3, ERROR = 4, READY = 5;
    public static final int MAX_SIDE = 4096, MAX_PIXELS = 4096 * 2160, MAX_MESSAGE = MAX_PIXELS * 4 + 8;
    public static final class Message {
        public final int type;
        public final byte[] bytes;
        Message(int type, byte[] bytes) { this.type = type; this.bytes = bytes; }
        public String text() { return new String(bytes, StandardCharsets.UTF_8); }
    }
    public static Message read(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) throw new IOException("Invalid browser protocol version");
        int type = input.readInt(), length = input.readInt();
        if (type < COMMAND || type > READY || length < 0 || length > MAX_MESSAGE)
            throw new IOException("Invalid browser message");
        byte[] bytes = new byte[length]; input.readFully(bytes);
        return new Message(type, bytes);
    }
    public static void write(DataOutputStream output, int type, byte[] bytes) throws IOException {
        if (bytes.length > MAX_MESSAGE) throw new IOException("Browser message too large");
        output.writeInt(MAGIC); output.writeInt(type); output.writeInt(bytes.length); output.write(bytes); output.flush();
    }
    public static String encode(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }
    public static String decode(String text) { return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8); }
    public static void validateSize(int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS)
            throw new IOException("Unsupported browser surface size");
    }
    private BrowserProtocol() {}
}
