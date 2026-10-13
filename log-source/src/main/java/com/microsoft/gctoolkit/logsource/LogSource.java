package com.microsoft.gctoolkit.logsource;

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

/** Shared source IO; callers own returned streams and must close them. */
public final class LogSource {

    public enum Format {
        PLAINTEXT, ZIP, GZIP, DIRECTORY
    }

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

    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    public static List<Path> files(Path directory) throws IOException {
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

    public static Stream<String> lines(Path path) throws IOException {
        switch (format(path)) {
            case PLAINTEXT:
                return plainLines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    public static Stream<String> plainLines(Path path) throws IOException {
        return Files.lines(path);
    }

    /** Opens only the first non-directory ZIP entry. */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return readerLines(input);
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(input, failure);
            throw failure;
        }
    }

    public static Stream<String> gzipLines(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return readerLines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(input, failure);
            throw failure;
        }
    }

    public static Stream<String> zipEntryLines(Path path, String name) throws IOException {
        ZipFile archive = new ZipFile(path.toFile());
        try {
            ZipEntry entry = archive.getEntry(name);
            if (entry == null) {
                throw new IOException("Missing ZIP entry " + name + " in " + path);
            }
            return readerLines(archive.getInputStream(entry)).onClose(() -> close(archive));
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(archive, failure);
            throw failure;
        }
    }

    private static Stream<String> readerLines(InputStream input) {
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

    private static void closeAfterFailure(java.io.Closeable resource, Exception failure) {
        try {
            resource.close();
        } catch (IOException closingFailure) {
            failure.addSuppressed(closingFailure);
        }
    }
}
