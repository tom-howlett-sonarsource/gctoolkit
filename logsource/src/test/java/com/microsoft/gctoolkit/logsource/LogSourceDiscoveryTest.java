// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.microsoft.gctoolkit.logsource.LogSources.LOG_CONTENT;
import static com.microsoft.gctoolkit.logsource.LogSources.writeGzip;
import static com.microsoft.gctoolkit.logsource.LogSources.writePlainText;
import static com.microsoft.gctoolkit.logsource.LogSources.writeZip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceDiscoveryTest {

    @TempDir
    Path directory;

    @Test
    void discoversTheFormatOfEachKindOfSource() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceDiscovery.formatOf(writePlainText(directory, "gc.log")));
        assertEquals(LogSourceFormat.GZIP, LogSourceDiscovery.formatOf(writeGzip(directory, "gc.log.gz")));
        assertEquals(LogSourceFormat.ZIP, LogSourceDiscovery.formatOf(writeZip(directory, "gc.log.zip", "gc.log")));
        assertEquals(LogSourceFormat.DIRECTORY, LogSourceDiscovery.formatOf(directory));
    }

    @Test
    void reportsAnUnreadableSourceAsPlainText() {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceDiscovery.formatOf(directory.resolve("missing.log")));
    }

    @Test
    void sizesTheSourceInBytes() throws IOException {
        Path path = writePlainText(directory, "gc.log");
        assertEquals(LOG_CONTENT.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                LogSourceDiscovery.sizeInBytes(path));
    }

    @Test
    void sizesAnUnreadableSourceAsZeroBytes() {
        assertEquals(0L, LogSourceDiscovery.sizeInBytes(directory.resolve("missing.log")));
    }

    @Test
    void findsTheSourcesInADirectory() throws IOException {
        Path first = writePlainText(directory, "gc.log");
        Path second = writePlainText(directory, "gc.log.1");

        List<Path> paths = LogSourceDiscovery.pathsIn(directory);

        assertEquals(2, paths.size());
        assertTrue(paths.contains(first));
        assertTrue(paths.contains(second));
    }

    @Test
    void failsToListTheSourcesOfAFile() throws IOException {
        Path path = writePlainText(directory, "gc.log");
        assertThrows(IOException.class, () -> LogSourceDiscovery.pathsIn(path));
    }

    @Test
    void findsTheFileEntriesInAZipSource() throws IOException {
        Path path = writeZip(directory, "gc.zip", "segments/", "segments/gc.log", "segments/gc.log.1");

        assertEquals(List.of("segments/gc.log", "segments/gc.log.1"), LogSourceDiscovery.zipEntryNames(path));
    }

    @Test
    void failsToListTheEntriesOfASourceThatIsNotAZip() throws IOException {
        Path path = writePlainText(directory, "gc.log");
        assertThrows(IOException.class, () -> LogSourceDiscovery.zipEntryNames(path));
    }
}
