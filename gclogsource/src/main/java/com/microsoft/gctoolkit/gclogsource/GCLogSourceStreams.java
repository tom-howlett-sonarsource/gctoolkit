// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Stream utilities for GC log sources.
 */
public final class GCLogSourceStreams {

    private GCLogSourceStreams() {
    }

    /**
     * Stream lines from the path according to the source format. ZIP sources stream the first file entry.
     *
     * @param path source path
     * @param format source format
     * @return stream of lines
     * @throws IOException when the source cannot be read
     */
    public static Stream<String> lines(Path path, GCLogSourceFormat format) throws IOException {
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

    /**
     * Remove null, blank, and leading/trailing whitespace from log lines.
     *
     * @param lines source lines
     * @return normalized lines
     */
    public static Stream<String> normalized(Stream<String> lines) {
        return lines
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> s.length() > 0);
    }

    private static Stream<String> zipLines(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry = zipStream.getNextEntry();
        while (entry != null && entry.isDirectory()) {
            entry = zipStream.getNextEntry();
        }
        if (entry == null) {
            zipStream.close();
            return Stream.empty();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(zipStream), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static Stream<String> gzipLines(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(gzipStream), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }
}
