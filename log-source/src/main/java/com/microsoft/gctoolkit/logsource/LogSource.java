package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
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

/** Shared source discovery and IO, independent of GC parsing and API models. */
public final class LogSource {
    public enum Format { PLAINTEXT, ZIP, GZIP, DIRECTORY }

    private LogSource() {
    }

    public static boolean magic(Path path, int first, int second) throws IOException {
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

    /** Returns immediate directory children without retaining an open directory stream. */
    public static List<Path> discover(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toList());
        }
    }

    public static List<String> zipEntries(Path path) throws IOException {
        try (ZipFile archive = new ZipFile(path.toFile())) {
            return archive.stream().filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName).collect(Collectors.toList());
        }
    }

    /** Returns the stored file size in bytes, not the decompressed size. */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /** The caller must close the returned stream, including after short-circuit operations. */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, format(path));
    }

    public static Stream<String> lines(Path path, Format format) throws IOException {
        if (format == Format.PLAINTEXT) {
            return Files.lines(path);
        }
        if (format == Format.DIRECTORY) {
            throw new IOException("Unable to read " + path);
        }
        InputStream input = Files.newInputStream(path);
        try {
            if (format == Format.GZIP) {
                input = new GZIPInputStream(input);
            } else {
                ZipInputStream archive = new ZipInputStream(input);
                input = archive;
                ZipEntry entry;
                do {
                    entry = archive.getNextEntry();
                } while (entry != null && entry.isDirectory());
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
            return reader.lines().onClose(() -> close(reader));
        } catch (IOException | RuntimeException failure) {
            try {
                input.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public static Stream<String> zipLines(Path path, String entryName) throws IOException {
        ZipFile archive = new ZipFile(path.toFile());
        try {
            ZipEntry entry = archive.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Unable to read ZIP entry " + entryName + " in " + path);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(archive.getInputStream(entry)));
            return reader.lines().onClose(() -> close(reader)).onClose(() -> close(archive));
        } catch (IOException | RuntimeException failure) {
            try {
                archive.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static void close(Closeable resource) {
        try {
            resource.close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
