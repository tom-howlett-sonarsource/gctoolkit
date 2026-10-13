package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** File-system and archive IO shared by log consumers. */
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

    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

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

    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, format(path));
    }

    public static Stream<String> lines(Path path, Format format) throws IOException {
        switch (format) {
            case PLAINTEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines(path);
            case GZIP:
                return gzipLines(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(input);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    public static Stream<String> gzipLines(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return lines(new GZIPInputStream(input));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(input, exception);
            throw exception;
        }
    }

    public static Stream<String> zipEntryLines(Path path, String name) throws IOException {
        ZipFile archive = new ZipFile(path.toFile());
        try {
            ZipEntry entry = archive.getEntry(name);
            if (entry == null) {
                throw new IOException("Missing ZIP entry: " + name);
            }
            return lines(archive.getInputStream(entry)).onClose(() -> close(archive));
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(archive, exception);
            throw exception;
        }
    }

    /** Concatenates entry bytes in archive order, without inserting line separators. */
    public static Stream<String> allZipLines(Path path) throws IOException {
        ZipFile archive = new ZipFile(path.toFile());
        Vector<InputStream> inputs = new Vector<>();
        try {
            List<? extends ZipEntry> entries = archive.stream()
                    .filter(entry -> !entry.isDirectory()).collect(Collectors.toList());
            for (ZipEntry entry : entries) {
                inputs.add(archive.getInputStream(entry));
            }
            return lines(new SequenceInputStream(inputs.elements())).onClose(() -> close(archive));
        } catch (IOException | RuntimeException exception) {
            for (InputStream input : inputs) {
                closeAfterFailure(input, exception);
            }
            closeAfterFailure(archive, exception);
            throw exception;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(Closeable resource) {
        try {
            resource.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void closeAfterFailure(Closeable resource, Exception failure) {
        try {
            resource.close();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }
}
