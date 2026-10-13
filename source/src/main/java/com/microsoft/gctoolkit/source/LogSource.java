package com.microsoft.gctoolkit.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** File-system and archive IO shared by log consumers. Returned streams must be closed. */
public final class LogSource {
    public enum Format { PLAINTEXT, ZIP, GZIP, DIRECTORY }

    private LogSource() {
    }

    public static boolean hasMagic(Path path, int first, int second) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return input.read() == first && input.read() == second;
        }
    }

    public static Format format(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == 0x1f && second == 0x8b) {
                return Format.GZIP;
            }
            if (first == 0x50 && second == 0x4b) {
                return Format.ZIP;
            }
            return Format.PLAINTEXT;
        }
    }

    /** Lists immediate children, preserving file-system encounter order. */
    public static List<Path> discover(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    /** Returns stored bytes, summing regular files recursively for a directory. */
    public static long size(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return Files.size(path);
        }
        try (Stream<Path> paths = Files.walk(path)) {
            long bytes = 0;
            for (Path file : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                bytes += Files.size(file);
            }
            return bytes;
        }
    }

    public static List<String> zipEntries(Path path) throws IOException {
        try (ZipFile archive = new ZipFile(path.toFile())) {
            return archive.stream().filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName).collect(Collectors.toList());
        }
    }

    public static Stream<String> open(Path path) throws IOException {
        switch (format(path)) {
            case PLAINTEXT: return openPlain(path);
            case ZIP: return openZip(path);
            case GZIP: return openGzip(path);
            default: throw new IOException("Unable to read " + path);
        }
    }

    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

    /** Opens the first non-directory ZIP entry; an empty ZIP produces an empty stream. */
    public static Stream<String> openZip(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(input);
        } catch (IOException | RuntimeException failure) {
            input.close();
            throw failure;
        }
    }

    public static Stream<String> openGzip(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException failure) {
            input.close();
            throw failure;
        }
    }

    public static Stream<String> openZipEntry(Path path, String name) throws IOException {
        ZipFile archive = new ZipFile(path.toFile());
        try {
            ZipEntry entry = archive.getEntry(name);
            if (entry == null) {
                throw new IOException("Missing ZIP entry " + name + " in " + path);
            }
            return lines(archive.getInputStream(entry)).onClose(() -> close(archive));
        } catch (IOException | RuntimeException failure) {
            archive.close();
            throw failure;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(java.io.Closeable resource) {
        try {
            resource.close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
