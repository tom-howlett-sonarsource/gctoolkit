// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Operations common to file-backed GC log sources. */
public final class LogSource {

    private static final int GZIP_MAGIC = 0x1f8b;
    private static final int ZIP_MAGIC = 0x504b;

    private LogSource() {
    }

    /** The kind of source found at a path. */
    public enum Kind {
        PLAIN_TEXT,
        ZIP,
        GZIP,
        DIRECTORY
    }

    /** Discovers a source kind using its type and magic bytes, not its extension. */
    public static Kind discover(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Kind.DIRECTORY;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            int magic = (first << 8) | second;
            if (magic == GZIP_MAGIC) {
                return Kind.GZIP;
            }
            if (magic == ZIP_MAGIC) {
                return Kind.ZIP;
            }
            return Kind.PLAIN_TEXT;
        }
    }

    /** Returns the source's physical size in bytes. */
    public static long byteSize(Path path) throws IOException {
        return Files.size(path);
    }

    /** Opens the content of a plain file, gzip file, or the first file in a zip. */
    public static InputStream open(Path path) throws IOException {
        Kind kind = discover(path);
        if (kind == Kind.DIRECTORY) {
            throw new IOException("Cannot open a directory as a log stream: " + path);
        }

        InputStream input = Files.newInputStream(path);
        try {
            if (kind == Kind.GZIP) {
                return new GZIPInputStream(input);
            }
            if (kind == Kind.ZIP) {
                ZipInputStream zip = new ZipInputStream(input);
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null && entry.isDirectory()) {
                    // Find the first file entry.
                }
                if (entry == null) {
                    zip.close();
                    throw new IOException("Zip source contains no file entries: " + path);
                }
                return zip;
            }
            return input;
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    /** Opens source content as UTF-8 lines; closing the stream closes the source. */
    public static Stream<String> lines(Path path) throws IOException {
        return lines(path, StandardCharsets.UTF_8);
    }

    /** Opens source content as lines; closing the stream closes the source. */
    public static Stream<String> lines(Path path, Charset charset) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(open(path)), charset));
        return reader.lines().onClose(() -> close(reader));
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Stream.close cannot report checked exceptions.
        }
    }
}
