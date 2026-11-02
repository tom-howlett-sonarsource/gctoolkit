// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Opens GZIP log files as line streams. Callers must close the returned stream,
 * even after a terminal operation, to release the reader and file resources.
 */
public final class GZipLogFile {

    private GZipLogFile() {
        // Utility class.
    }

    /**
     * Stream the lines of a GZIP file.
     * @param path the GZIP file path
     * @return lines owning the file resources
     * @throws IOException if the file cannot be opened
     */
    public static Stream<String> stream(Path path) throws IOException {
        InputStream file = Files.newInputStream(path);
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new BufferedInputStream(new GZIPInputStream(file))));
            return reader.lines().onClose(() -> close(reader));
        } catch (IOException | RuntimeException failure) {
            try {
                file.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
