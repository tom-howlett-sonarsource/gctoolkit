package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class LogSource {

    /** First GZIP signature byte. */
    private static final int GZIP_MAGIC_BYTE_ONE = 0x1F;
    /** Second GZIP signature byte. */
    private static final int GZIP_MAGIC_BYTE_TWO = 0x8B;
    /** First ZIP signature byte. */
    private static final int ZIP_MAGIC_BYTE_ONE = 0x50;
    /** Second ZIP signature byte. */
    private static final int ZIP_MAGIC_BYTE_TWO = 0x4B;

    private LogSource() {
    }

    /**
     * Discovers a source type from its file-system state and signature bytes.
     *
     * @param path source path
     * @return discovered source type
     * @throws IOException when the source cannot be read
     */
    public static LogSourceType discover(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return LogSourceType.DIRECTORY;
        }

        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path))) {
            int firstByte = input.read();
            int secondByte = input.read();
            if (firstByte == GZIP_MAGIC_BYTE_ONE
                    && secondByte == GZIP_MAGIC_BYTE_TWO) {
                return LogSourceType.GZIP;
            }
            if (firstByte == ZIP_MAGIC_BYTE_ONE
                    && secondByte == ZIP_MAGIC_BYTE_TWO) {
                return LogSourceType.ZIP;
            }
            return LogSourceType.PLAIN_TEXT;
        }
    }

    /**
     * Returns the number of bytes in a source file.
     *
     * @param path source path
     * @return source size in bytes
     * @throws IOException when the source size cannot be read
     */
    public static long byteSize(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return Files.size(path);
    }

    /**
     * Opens lines from a plain, ZIP, or GZIP log source.
     *
     * @param path source path
     * @return lazily read source lines
     * @throws IOException when the source cannot be opened
     */
    public static Stream<String> lines(final Path path) throws IOException {
        LogSourceType sourceType = discover(path);
        switch (sourceType) {
            case PLAIN_TEXT:
                return Files.lines(path);
            case ZIP:
                return zipLines(path, null);
            case GZIP:
                return readerLines(new GZIPInputStream(
                        Files.newInputStream(path)));
            case DIRECTORY:
            default:
                throw new IOException("Unable to read log source " + path);
        }
    }

    /**
     * Opens lines from a named ZIP archive entry.
     *
     * @param path ZIP source path
     * @param zipEntryName archive entry name
     * @return lazily read entry lines
     * @throws IOException when the source or entry cannot be opened
     */
    public static Stream<String> lines(final Path path,
                                       final String zipEntryName)
            throws IOException {
        Objects.requireNonNull(zipEntryName, "zipEntryName");
        return zipLines(path, zipEntryName);
    }

    private static Stream<String> zipLines(final Path path,
                                           final String zipEntryName)
            throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        try {
            ZipEntry entry = nextEntry(input, zipEntryName);
            if (entry == null) {
                String message = zipEntryName == null
                        ? "ZIP log source contains no files: " + path
                        : "ZIP log source does not contain "
                                + zipEntryName + ": " + path;
                throw new IOException(message);
            }
            return readerLines(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private static ZipEntry nextEntry(final ZipInputStream input,
                                      final String zipEntryName)
            throws IOException {
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null) {
            boolean requestedEntry = zipEntryName == null
                    || zipEntryName.equals(entry.getName());
            if (!entry.isDirectory() && requestedEntry) {
                return entry;
            }
        }
        return null;
    }

    private static Stream<String> readerLines(final InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(input)));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(final BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
