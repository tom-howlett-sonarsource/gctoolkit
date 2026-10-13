package com.microsoft.gctoolkit.source;

import java.io.BufferedReader;
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

public final class LogSource {

    public enum Format {
        PLAINTEXT, ZIP, GZIP, DIRECTORY
    }

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

    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, discover(path));
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
                return readerLines(new GZIPInputStream(input));
            }
            ZipInputStream archive = new ZipInputStream(input);
            input = archive;
            ZipEntry entry;
            do {
                entry = archive.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return readerLines(archive);
        } catch (IOException | RuntimeException failure) {
            try {
                input.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public static Stream<String> zipLines(Path path, String name) throws IOException {
        ZipFile archive = new ZipFile(path.toFile());
        try {
            ZipEntry entry = archive.getEntry(name);
            if (entry == null) {
                throw new IOException("No ZIP entry " + name + " in " + path);
            }
            return readerLines(archive.getInputStream(entry)).onClose(() -> close(archive));
        } catch (IOException | RuntimeException failure) {
            try {
                archive.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public static Stream<String> allZipLines(Path path) throws IOException {
        ZipFile archive = new ZipFile(path.toFile());
        Vector<InputStream> inputs = new Vector<>();
        try {
            List<? extends ZipEntry> entries = archive.stream()
                    .filter(entry -> !entry.isDirectory()).collect(Collectors.toList());
            for (ZipEntry entry : entries) {
                inputs.add(archive.getInputStream(entry));
            }
            return readerLines(new SequenceInputStream(inputs.elements()))
                    .onClose(() -> close(archive));
        } catch (IOException | RuntimeException failure) {
            try {
                archive.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static Stream<String> readerLines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
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
