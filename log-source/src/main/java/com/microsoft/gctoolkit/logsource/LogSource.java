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

/** File-system and archive operations shared by log consumers. */
public final class LogSource {
    public enum Format { PLAINTEXT, DIRECTORY, ZIP, GZIP }

    private LogSource() {
    }

    public static boolean magic(Path path, int first, int second) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return input.read() == first && input.read() == second;
        }
    }

    public static Format discover(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (magic(path, 0x1f, 0x8b)) {
            return Format.GZIP;
        }
        return magic(path, 0x50, 0x4b) ? Format.ZIP : Format.PLAINTEXT;
    }

    /** Returns the on-disk byte size, not the decompressed size. */
    public static long size(Path path) throws IOException {
        return Files.size(path);
    }

    public static List<Path> list(Path directory) throws IOException {
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

    /** The returned stream owns its underlying file and must be closed. */
    public static Stream<String> open(Path path) throws IOException {
        switch (discover(path)) {
            case PLAINTEXT:
                return openPlain(path);
            case ZIP:
                return openZip(path);
            case GZIP:
                return openGzip(path);
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    public static Stream<String> openPlain(Path path) throws IOException {
        return Files.lines(path);
    }

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
            return lines(archive.getInputStream(entry)).onClose(() -> {
                try {
                    archive.close();
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            });
        } catch (IOException | RuntimeException failure) {
            archive.close();
            throw failure;
        }
    }

    private static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input)));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
    }
}
