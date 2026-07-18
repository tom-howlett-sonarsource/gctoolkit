package com.microsoft.gctoolkit.io.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Shared file-system operations for GC log sources.
 */
public final class GCLogSource {

    /** First byte of the GZIP magic number. */
    private static final int GZIP_MAGIC_FIRST = 0x1F;
    /** Second byte of the GZIP magic number. */
    private static final int GZIP_MAGIC_SECOND = 0x8B;
    /** First byte of the ZIP magic number. */
    private static final int ZIP_MAGIC_FIRST = 0x50;
    /** Second byte of the ZIP magic number. */
    private static final int ZIP_MAGIC_SECOND = 0x4B;

    private GCLogSource() {
    }

    /**
     * Discovers the format of a GC log source.
     *
     * @param path source path
     * @return discovered source format
     * @throws IOException when the source cannot be read
     */
    public static Format discover(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }

        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                return Format.GZIP;
            }
            if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
                return Format.ZIP;
            }
            return Format.PLAIN_TEXT;
        }
    }

    /**
     * Returns the source size in bytes.
     *
     * @param path source path
     * @return source byte size
     * @throws IOException when the source size cannot be read
     */
    public static long size(final Path path) throws IOException {
        return Files.size(Objects.requireNonNull(path, "path"));
    }

    /**
     * Lists paths directly contained by a directory.
     *
     * @param directory directory to inspect
     * @return contained paths
     * @throws IOException when the directory cannot be listed
     */
    public static List<Path> list(final Path directory) throws IOException {
        Path sourceDirectory = Objects.requireNonNull(directory, "directory");
        try (Stream<Path> paths = Files.list(sourceDirectory)) {
            return paths.sorted().collect(Collectors.toList());
        }
    }

    /**
     * Lists non-directory entries in a ZIP source.
     *
     * @param path ZIP source path
     * @return entry names in archive order
     * @throws IOException when the ZIP source cannot be read
     */
    public static List<String> zipEntries(final Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path");
        try (ZipFile zipFile = new ZipFile(source.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Opens a line stream for a plain, ZIP, or GZIP source.
     * ZIP sources expose the first non-directory entry.
     *
     * @param path source path
     * @return source lines
     * @throws IOException when the source cannot be opened
     */
    public static Stream<String> open(final Path path) throws IOException {
        return open(path, discover(path));
    }

    /**
     * Opens a line stream for a source with a known format.
     *
     * @param path source path
     * @param format source format
     * @return source lines
     * @throws IOException when the source cannot be opened
     */
    public static Stream<String> open(final Path path, final Format format)
            throws IOException {
        Objects.requireNonNull(path, "path");
        switch (Objects.requireNonNull(format, "format")) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return openFirstZipEntry(path);
            case GZIP:
                return lines(new GZIPInputStream(Files.newInputStream(path)));
            default:
                throw new IOException("Unable to read " + path);
        }
    }

    /**
     * Opens a named ZIP entry as a line stream.
     *
     * @param path ZIP source path
     * @param entryName entry name
     * @return entry lines
     * @throws IOException when the ZIP source or entry cannot be opened
     */
    public static Stream<String> openZipEntry(
            final Path path, final String entryName) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(entryName, "entryName");
        ZipInputStream zipStream = new ZipInputStream(
                Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null
                    && (entry.isDirectory()
                    || !entryName.equals(entry.getName())));
            if (entry == null) {
                zipStream.close();
                throw missingZipEntry(path, entryName);
            }
            return lines(zipStream);
        } catch (IOException | RuntimeException exception) {
            zipStream.close();
            throw exception;
        }
    }

    private static Stream<String> openFirstZipEntry(final Path path)
            throws IOException {
        ZipInputStream zipStream = new ZipInputStream(
                Files.newInputStream(path));
        try {
            ZipEntry entry;
            do {
                entry = zipStream.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(zipStream);
        } catch (IOException | RuntimeException exception) {
            zipStream.close();
            throw exception;
        }
    }

    private static Stream<String> lines(final InputStream input) {
        BufferedReader reader = reader(input);
        return reader.lines().onClose(() -> close(reader));
    }

    private static BufferedReader reader(final InputStream input) {
        return new BufferedReader(
                new InputStreamReader(
                        new BufferedInputStream(input),
                        Charset.defaultCharset()));
    }

    private static IOException missingZipEntry(
            final Path path, final String entryName) {
        return new IOException(
                "Unable to read ZIP entry " + entryName + " from " + path);
    }

    private static void close(final Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Supported GC log source formats.
     */
    public enum Format {
        /** ZIP archive. */
        ZIP,
        /** GZIP stream. */
        GZIP,
        /** Uncompressed text file. */
        PLAIN_TEXT,
        /** File-system directory. */
        DIRECTORY
    }
}
