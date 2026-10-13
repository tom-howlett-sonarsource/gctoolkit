package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

final class ArchiveStreams {

    private ArchiveStreams() {
    }

    static Stream<String> lines(BufferedReader reader, Closeable archive) {
        return reader.lines().onClose(() -> {
            try (Closeable ownedArchive = archive; BufferedReader ownedReader = reader) {
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    static void closeAfterFailure(Closeable archive, Throwable failure) {
        try {
            archive.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
