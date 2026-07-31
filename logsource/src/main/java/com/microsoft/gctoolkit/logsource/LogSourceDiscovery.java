// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

/**
 * Discovers what a log source is, how big it is and which log sources it contains.
 */
public final class LogSourceDiscovery {

    private static final Logger LOG = Logger.getLogger(LogSourceDiscovery.class.getName());

    private static final int GZIP_MAGIC1 = 0x1F;
    private static final int GZIP_MAGIC2 = 0x8b;

    private static final int ZIP_MAGIC1 = 0x50;
    private static final int ZIP_MAGIC2 = 0x4b;

    private LogSourceDiscovery() {
        // Utility class.
    }

    /**
     * Determine the format of the log source by looking at the leading, magic bytes of the file.
     * A source that cannot be recognised as a directory or as a compressed file is reported as
     * {@link LogSourceFormat#PLAINTEXT}.
     * @param path The path to the log source.
     * @return The format of the log source.
     */
    public static LogSourceFormat formatOf(Path path) {
        if (Files.isDirectory(path))
            return LogSourceFormat.DIRECTORY;
        else if (hasMagic(path, GZIP_MAGIC1, GZIP_MAGIC2))
            return LogSourceFormat.GZIP;
        else if (hasMagic(path, ZIP_MAGIC1, ZIP_MAGIC2))
            return LogSourceFormat.ZIP;
        return LogSourceFormat.PLAINTEXT;
    }

    private static boolean hasMagic(Path path, int first, int second) {
        try (InputStream magicByteReader = Files.newInputStream(path)) {
            return magicByteReader.read() == first && magicByteReader.read() == second;
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    /**
     * The size, in bytes, of the log source.
     * @param path The path to the log source.
     * @return The number of bytes in the log source, or {@code 0} if the size cannot be determined.
     */
    public static long sizeInBytes(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to determine the size of " + path, ioe);
        }
        return 0L;
    }

    /**
     * List the log sources held in a directory. Unlike {@link Files#list(Path)}, the returned list
     * does not hold on to an open handle on the directory.
     * @param directory The path to the directory.
     * @return The paths of the entries in the directory.
     * @throws IOException If the directory cannot be read.
     */
    public static List<Path> pathsIn(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(toList());
        }
    }

    /**
     * List the names of the log sources held in a Zip file. Directory entries are not included.
     * @param path The path to the Zip file.
     * @return The names of the file entries in the Zip file.
     * @throws IOException If the Zip file cannot be read.
     */
    public static List<String> zipEntryNames(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(toList());
        }
    }
}
