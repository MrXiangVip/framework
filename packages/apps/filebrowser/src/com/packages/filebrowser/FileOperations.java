package com.packages.filebrowser;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Filesystem operations for the desktop app; no shell commands or implicit overwrites. */
public final class FileOperations {
    public static final class Entry {
        public final Path path;
        public final String name;
        public final boolean directory, link;
        public final long size, modified;
        Entry(Path path) throws IOException {
            this.path = path; name = path.getFileName().toString();
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            link = attrs.isSymbolicLink(); directory = Files.isDirectory(path);
            size = attrs.size(); modified = attrs.lastModifiedTime().toMillis();
        }
    }
    public static final class Listing {
        public final Path directory;
        public final List<Entry> entries;
        public final boolean limited;
        Listing(Path directory, List<Entry> entries, boolean limited) { this.directory = directory; this.entries = entries; this.limited = limited; }
    }
    public static Listing list(Path directory, boolean hidden) throws IOException {
        Path resolved = directory.toRealPath();
        List<Entry> entries = new ArrayList<>(); boolean limited = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolved)) {
            for (Path path : stream) {
                if (!hidden && (path.getFileName().toString().startsWith(".") || Files.isHidden(path))) continue;
                if (entries.size() >= 10000) { limited = true; break; }
                try { entries.add(new Entry(path)); }
                catch (NoSuchFileException ignored) { /* An entry disappeared while listing. */ }
            }
        }
        entries.sort(Comparator.comparing((Entry e) -> !e.directory)
                .thenComparing(e -> e.name.toLowerCase(Locale.ROOT)).thenComparing(e -> e.name));
        return new Listing(resolved, entries, limited);
    }
    public static Path named(Path directory, String name) throws IOException {
        if (name.isEmpty() || name.trim().isEmpty() || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\") || name.codePoints().anyMatch(Character::isISOControl))
            throw new IOException("Enter a single valid name, without / or \\");
        Path target = directory.resolve(name);
        if (!target.getParent().equals(directory)) throw new IOException("Invalid destination name");
        return target;
    }
    public static Path createFolder(Path directory, String name) throws IOException {
        return Files.createDirectory(named(directory, name));
    }
    public static Path rename(Path source, String name) throws IOException {
        Path target = named(source.getParent(), name);
        if (source.equals(target)) return source;
        return Files.move(source, target);
    }
    public static Path transfer(Path source, Path destination, boolean move) throws IOException {
        source = source.toAbsolutePath().normalize(); destination = destination.toRealPath();
        if (source.getFileName() == null) throw new IOException("Cannot transfer filesystem root");
        Path target = destination.resolve(source.getFileName());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) && destination.startsWith(source.toRealPath()))
            throw new IOException("Cannot copy or move a folder into itself");
        if (move) return Files.move(source, target); // Cross-filesystem failures leave source intact.
        Path staging = Files.createTempDirectory(destination, ".filebrowser-copy-");
        try {
            Path payload = staging.resolve("payload");
            final Path origin = source;
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectory(payload.resolve(origin.relativize(dir))); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, payload.resolve(origin.relativize(file)), LinkOption.NOFOLLOW_LINKS);
                    return FileVisitResult.CONTINUE;
                }
            });
            return Files.move(payload, target);
        } finally { delete(staging); }
    }
    public static void delete(Path path) throws IOException {
        if (path.toAbsolutePath().normalize().getFileName() == null) throw new IOException("Cannot delete filesystem root");
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }
    public static final class Preview {
        public BufferedImage image;
        public String text;
        public boolean limited;
    }
    public static Preview preview(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Preview supports regular files only");
        Preview preview = new Preview();
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.matches(".*\\.(png|jpe?g|gif|bmp)")) {
            if (Files.size(path) > 20 * 1024 * 1024) throw new IOException("Image is larger than 20 MB");
            try (ImageInputStream stream = ImageIO.createImageInputStream(path.toFile())) {
                if (stream == null) throw new IOException("Cannot open image");
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IOException("Unsupported image format");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(stream);
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 20000000L) throw new IOException("Image exceeds 20 megapixels");
                    preview.image = reader.read(0);
                } finally { reader.dispose(); }
            }
        } else {
            try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8.newDecoder())) {
                char[] chars = new char[32768]; int length = 0, count;
                while (length < chars.length && (count = reader.read(chars, length, chars.length - length)) != -1) length += count;
                preview.limited = reader.read() != -1;
                preview.text = new String(chars, 0, length);
                if (preview.text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
                    throw new IOException("Binary file: no text preview");
            }
        }
        return preview;
    }
}
