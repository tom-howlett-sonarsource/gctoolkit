// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** File-system operations shared by production log data sources. */
public final class LogFileSource {
    private static final int GZIP_MAGIC = 0x1f8b;
    private static final int ZIP_MAGIC = 0x504b;

    private LogFileSource() {
    }

    /** Supported source formats. */
    public enum Format {
        ZIP, GZIP, PLAINTEXT, DIRECTORY, UNKNOWN
    }

    /** Discovers a source format from its type and leading magic bytes. */
    public static Format discover(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return Format.DIRECTORY;
        }
        if (!Files.isRegularFile(path)) {
            return Format.UNKNOWN;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            int magic = (first << 8) | second;
            if (magic == GZIP_MAGIC) {
                return Format.GZIP;
            }
            if (magic == ZIP_MAGIC) {
                return Format.ZIP;
            }
            return Format.PLAINTEXT;
        }
    }

    /** Returns the stored byte size of a file, or all regular files below a directory. */
    public static long byteSize(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isDirectory(path)) {
            return Files.size(path);
        }
        try (Stream<Path> paths = Files.walk(path)) {
            try {
                return paths.filter(Files::isRegularFile).mapToLong(LogFileSource::sizeUnchecked).sum();
            } catch (SourceIOException exception) {
                throw (IOException) exception.getCause();
            }
        }
    }

    /** Opens the first log stream in a plain, ZIP, or GZIP source. */
    public static Stream<String> open(Path path) throws IOException {
        Format format = discover(path);
        if (format == Format.PLAINTEXT) {
            return Files.lines(path);
        }
        if (format == Format.GZIP) {
            return lines(new GZIPInputStream(Files.newInputStream(path)));
        }
        if (format == Format.ZIP) {
            return zipLines(path);
        }
        throw new IOException("Unable to read " + path);
    }

    private static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null && entry.isDirectory()) {
            // Skip archive directories; the first file is the source.
        }
        if (entry == null) {
            input.close();
            throw new IOException("ZIP source contains no files: " + path);
        }
        return lines(input);
    }

    private static Stream<String> lines(InputStream input) {
        return new BufferedReader(new InputStreamReader(new BufferedInputStream(input))).lines();
    }

    private static long sizeUnchecked(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new SourceIOException(exception);
        }
    }

    private static final class SourceIOException extends RuntimeException {
        private SourceIOException(IOException cause) {
            super(cause);
        }
    }
}
