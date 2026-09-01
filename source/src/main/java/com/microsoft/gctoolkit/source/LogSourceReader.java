// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Opens GC log source streams consistently across modules.
 */
public final class LogSourceReader {

    private LogSourceReader() {}

    public static Stream<String> stream(LogSourceMetadata metadata) throws IOException {
        if (metadata.isPlainText()) {
            return Files.lines(metadata.getPath());
        } else if (metadata.isZip()) {
            return streamFirstZipEntry(metadata.getPath());
        } else if (metadata.isGZip()) {
            return streamGZipFile(metadata.getPath());
        }
        throw new IOException("Unable to read " + metadata.getPath());
    }

    public static Stream<String> streamNonBlank(LogSourceMetadata metadata) throws IOException {
        return stream(metadata)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(line -> !line.isEmpty());
    }

    public static Stream<String> streamZipEntry(Path path, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            zipFile.close();
            throw new IOException("Unable to read " + entryName + " from " + path);
        }
        try {
            return lines(zipFile, new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            zipFile.close();
            throw ioe;
        }
    }

    private static Stream<String> streamFirstZipEntry(Path path) throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        Optional<? extends ZipEntry> entry = zipFile.stream()
                .filter(zipEntry -> !zipEntry.isDirectory())
                .findFirst();
        if (entry.isEmpty()) {
            zipFile.close();
            return Stream.empty();
        }
        try {
            return lines(zipFile, new InputStreamReader(zipFile.getInputStream(entry.get()), StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            zipFile.close();
            throw ioe;
        }
    }

    private static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return lines(new InputStreamReader(new BufferedInputStream(gzipStream), StandardCharsets.UTF_8));
    }

    private static Stream<String> lines(Reader reader) {
        BufferedReader bufferedReader = new BufferedReader(reader);
        return bufferedReader.lines().onClose(() -> close(bufferedReader));
    }

    private static Stream<String> lines(ZipFile zipFile, Reader reader) {
        BufferedReader bufferedReader = new BufferedReader(reader);
        return bufferedReader.lines().onClose(() -> {
            close(bufferedReader);
            close(zipFile);
        });
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception exception) {
            throw new UncheckedIOException(new IOException(exception));
        }
    }
}
