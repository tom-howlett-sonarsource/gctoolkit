// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTest {

    @TempDir
    Path directory;

    @Test
    void totalByteSizeIncludesActiveLogWhenConstructedFromRotatingLogMember() throws Exception {
        byte[] archived = "archived log\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active log\n".getBytes(StandardCharsets.UTF_8);
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Path archivedSegment = logs.resolve("gc.log.0");
        Files.write(archivedSegment, archived);
        Files.write(logs.resolve("gc.log"), active);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(archivedSegment);

        assertEquals(archived.length + active.length, metadata.getTotalByteSize());
    }

    @Test
    void totalByteSizeIsZeroWhenDirectoryHasNoEntries() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(logs);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void totalByteSizeIsZeroWhenZipHasNoFileEntries() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(archive);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void totalByteSizeIsZeroWhenZipCannotBeRead() throws Exception {
        Path archive = directory.resolve("logs.zip");
        Files.write(archive, new byte[] {'P', 'K'});

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(archive);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void totalByteSizeIsZeroWhenMemberParentCannotBeListed() throws Exception {
        Path parent = directory.resolve("not-a-directory");
        Files.write(parent, "not a directory\n".getBytes(StandardCharsets.UTF_8));
        Path log = parent.resolve("gc.log");

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(log);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void totalByteSizeTreatsUnreadableDiscoveredSegmentAsZero() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Path brokenLink = logs.resolve("gc.log");
        try {
            Files.createSymbolicLink(brokenLink, directory.resolve("missing.log"));
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            return;
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(logs);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void totalByteSizeSupportsRelativeRotatingLogMember() throws Exception {
        String baseName = "relative-rotating-size-" + System.nanoTime() + ".log";
        Path archived = Path.of(baseName + ".0");
        Path active = Path.of(baseName);
        byte[] archivedContent = "archived relative log\n".getBytes(StandardCharsets.UTF_8);
        byte[] activeContent = "active relative log\n".getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(archived, archivedContent);
            Files.write(active, activeContent);

            RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(archived);

            assertEquals(archivedContent.length + activeContent.length, metadata.getTotalByteSize());
        } finally {
            Files.deleteIfExists(archived);
            Files.deleteIfExists(active);
        }
    }
}
