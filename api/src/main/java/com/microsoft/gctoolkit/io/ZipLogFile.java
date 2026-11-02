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
 * Opens ZIP log entries as line streams. Callers must close the returned stream,
 * even after a terminal operation, to release the reader and archive resources.
 */
public final class ZipLogFile {

    private ZipLogFile() {
        // Utility class.
    }

    /**
     * Stream the first non-directory entry, or an empty stream for an empty archive.
     * @param path the archive path
     * @return lines owning the archive resources
     * @throws IOException if the archive cannot be opened
     */
    public static Stream<String> stream(Path path) throws IOException {
        return stream(new ZipInputStream(Files.newInputStream(path)));
    }

    /**
     * Stream a named entry, closing both its reader and the owning ZIP file on close.
     * @param path the archive path
     * @param entryName the entry name
     * @return lines owning the archive resources
     * @throws IOException if the archive or entry cannot be opened
     */
    public static Stream<String> stream(Path path, String entryName) throws IOException {
        return stream(new ZipFile(path.toFile()), entryName);
    }

    static Stream<String> stream(ZipInputStream archive) throws IOException {
        try {
            ZipEntry entry;
            do {
                entry = archive.getNextEntry();
            } while (entry != null && entry.isDirectory());
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new BufferedInputStream(archive)));
            return reader.lines().onClose(() -> close(reader));
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(archive, failure);
            throw failure;
        }
    }

    static Stream<String> stream(ZipFile archive, String entryName) throws IOException {
        try {
            ZipEntry entry = archive.getEntry(entryName);
            if (entry == null) {
                throw new IOException("Missing ZIP entry: " + entryName);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(archive.getInputStream(entry)));
            return reader.lines().onClose(() -> close(reader)).onClose(() -> close(archive));
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(archive, failure);
            throw failure;
        }
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
