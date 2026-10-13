package com.microsoft.gctoolkit.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Shared filesystem and archive operations, without GC-specific line processing. */
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

    /** Returns the stored source size, not the decompressed size. */
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

    /** Reads the first non-directory entry, matching single-log semantics. */
    public static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = input.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return readerLines(new BufferedInputStream(input));
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(input, failure);
            throw failure;
        }
    }

    public static Stream<String> gzipLines(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            return readerLines(new BufferedInputStream(new GZIPInputStream(input)));
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(input, failure);
            throw failure;
        }
    }

    public static Stream<String> zipEntryLines(Path path, String name) throws IOException {
        return archiveLines(path, Collections.singletonList(name));
    }

    /** Concatenates entry bytes in archive order, including partial final lines. */
    public static Stream<String> allZipLines(Path path) throws IOException {
        return archiveLines(path, null);
    }

    private static Stream<String> archiveLines(Path path, List<String> names) throws IOException {
        ZipFile archive = new ZipFile(path.toFile());
        try {
            List<String> selected = names == null
                    ? archive.stream().filter(entry -> !entry.isDirectory())
                            .map(ZipEntry::getName).collect(Collectors.toList())
                    : names;
            Vector<InputStream> inputs = new Vector<>();
            for (String name : selected) {
                ZipEntry entry = archive.getEntry(name);
                if (entry == null) {
                    throw new IOException("Missing ZIP entry " + name + " in " + path);
                }
                inputs.add(archive.getInputStream(entry));
            }
            return readerLines(new SequenceInputStream(inputs.elements()))
                    .onClose(() -> closeUnchecked(archive));
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(archive, failure);
            throw failure;
        }
    }

    private static Stream<String> readerLines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        return reader.lines().onClose(() -> closeUnchecked(reader));
    }

    private static void closeUnchecked(java.io.Closeable resource) {
        try {
            resource.close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void closeAfterFailure(java.io.Closeable resource, Exception failure) {
        try {
            resource.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
