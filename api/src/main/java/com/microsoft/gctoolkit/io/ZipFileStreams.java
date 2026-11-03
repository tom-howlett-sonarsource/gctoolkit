// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Line streams that own their ZIP readers and archives.
 */
public final class ZipFileStreams {

    private ZipFileStreams() {
    }

    /**
     * Stream the first non-directory entry, or an empty stream if there is none.
     * The caller must close the returned stream, including after short-circuit operations.
     *
     * @param path the ZIP archive
     * @return lines from the first file in archive order
     * @throws IOException if the archive cannot be opened or its first entry cannot be read
     */
    public static Stream<String> firstEntry(Path path) throws IOException {
        return firstEntry(new ZipInputStream(Files.newInputStream(path)));
    }

    static Stream<String> firstEntry(ZipInputStream archive) throws IOException {
        try {
            ZipEntry entry;
            do {
                entry = archive.getNextEntry();
            } while (entry != null && entry.isDirectory());
            return lines(new BufferedReader(new InputStreamReader(new BufferedInputStream(archive))));
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(archive, failure);
            throw failure;
        }
    }

    static Stream<String> entry(Path path, String name) throws IOException {
        return entry(new ZipFile(path.toFile()), name);
    }

    static Stream<String> entry(ZipFile archive, String name) throws IOException {
        try {
            ZipEntry entry = archive.getEntry(name);
            if (entry == null) {
                throw new IOException("ZIP entry not found: " + name);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(archive.getInputStream(entry)));
            return lines(reader).onClose(() -> close(archive));
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(archive, failure);
            throw failure;
        }
    }

    private static Stream<String> lines(BufferedReader reader) {
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(Closeable resource) {
        try {
            resource.close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void closeAfterFailure(Closeable resource, Throwable failure) {
        try {
            resource.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
