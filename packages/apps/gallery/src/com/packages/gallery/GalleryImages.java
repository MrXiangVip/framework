package com.packages.gallery;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Bounded directory scan and subsampled decoding; neither operation changes photo files. */
public final class GalleryImages {
    public static final class Photo {
        public final Path path;
        public final String label;
        Photo(Path path, String label) { this.path = path; this.label = label; }
    }
    public static final class Album {
        public final Path directory;
        public final List<Photo> photos = new ArrayList<>();
        public boolean limited;
        public int skipped;
        Album(Path directory) { this.directory = directory; }
    }
    public static Album scan(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        if (Files.notExists(absolute)) return new Album(absolute);
        final Path root = absolute.toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("Choose an image directory");
        final Album album = new Album(root);
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 8, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
                if (!path.equals(root) && path.getFileName().toString().startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && !path.getFileName().toString().startsWith(".")
                        && path.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|gif|bmp)")) {
                    if (album.photos.size() >= 3000) { album.limited = true; return FileVisitResult.TERMINATE; }
                    album.photos.add(new Photo(path, root.relativize(path).toString()));
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path, IOException error) throws IOException {
                if (path.equals(root)) throw error;
                album.skipped++; return FileVisitResult.CONTINUE;
            }
        });
        album.photos.sort(Comparator.comparing((Photo photo) -> photo.label.toLowerCase(Locale.ROOT)).thenComparing(photo -> photo.label));
        return album;
    }
    public static final class Decoded {
        public final BufferedImage image;
        public final int width, height;
        Decoded(BufferedImage image, int width, int height) { this.image = image; this.width = width; this.height = height; }
    }
    public static Decoded decode(Path file, int maxEdge) throws IOException {
        if (maxEdge < 1 || maxEdge > 2048) throw new IllegalArgumentException("Decode edge must be 1..2048");
        if (!Files.isRegularFile(file) || Files.size(file) > 40L * 1024 * 1024) throw new IOException("Image missing or larger than 40 MB");
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw new IOException("Cannot read image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported or damaged image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 100000000L) throw new IOException("Image exceeds 100 megapixels");
                int sample = Math.max(1, (Math.max(width, height) + maxEdge - 1) / maxEdge);
                ImageReadParam options = reader.getDefaultReadParam();
                options.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage image = reader.read(0, options);
                if (image == null) throw new IOException("Cannot decode image");
                return new Decoded(image, width, height);
            } finally { reader.dispose(); }
        }
    }
}
