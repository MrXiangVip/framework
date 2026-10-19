package com.packages.settings;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Validated, atomically replaced local Settings preferences. */
public final class SettingsStore {
    private final Path file;
    public static final class Values {
        public final String name, theme;
        public final int textSize;
        public final boolean clock24;
        public Values(String name, String theme, int textSize, boolean clock24) {
            this.name = name.trim(); this.theme = theme; this.textSize = textSize; this.clock24 = clock24;
            if (this.name.isEmpty() || this.name.codePointCount(0, this.name.length()) > 40
                    || this.name.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Name must contain 1-40 characters");
            if (!"light".equals(theme) && !"dark".equals(theme)) throw new IllegalArgumentException("Invalid theme");
            if (textSize != 14 && textSize != 16 && textSize != 18) throw new IllegalArgumentException("Invalid text size");
        }
        public static Values defaults() { return new Values("Framework Desktop", "light", 16, true); }
    }
    public SettingsStore(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public Values load() throws IOException {
        if (Files.notExists(file)) return Values.defaults();
        if (Files.size(file) > 16384) throw new IOException("Settings file is too large");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); } catch (IllegalArgumentException e) { throw new IOException("Malformed settings properties", e); }
        try {
            if (!"1".equals(properties.getProperty("version"))) throw new IllegalArgumentException("Unsupported settings version");
            String clock = properties.getProperty("clock24");
            if (!"true".equals(clock) && !"false".equals(clock)) throw new IllegalArgumentException("Invalid time format");
            return new Values(properties.getProperty("name", ""), properties.getProperty("theme", ""),
                    Integer.parseInt(properties.getProperty("textSize", "")), Boolean.parseBoolean(clock));
        } catch (IllegalArgumentException e) { throw new IOException("Invalid settings: " + e.getMessage(), e); }
    }
    public void save(Values values) throws IOException {
        Files.createDirectories(file.getParent());
        try (FileChannel channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = channel.lock()) {
            Path temporary = Files.createTempFile(file.getParent(), "settings-", ".tmp");
            try {
                Properties properties = new Properties();
                properties.setProperty("version", "1"); properties.setProperty("name", values.name);
                properties.setProperty("theme", values.theme); properties.setProperty("textSize", "" + values.textSize);
                properties.setProperty("clock24", "" + values.clock24);
                try (OutputStream output = Files.newOutputStream(temporary)) { properties.store(output, "Framework Settings"); }
                try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        }
    }
}
