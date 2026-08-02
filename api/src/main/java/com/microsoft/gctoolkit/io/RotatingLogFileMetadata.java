// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public class RotatingLogFileMetadata extends LogFileMetadata {

    private static final Logger LOG = Logger.getLogger(RotatingLogFileMetadata.class.getName());

    private List<LogFileSegment> segments;

    public RotatingLogFileMetadata(Path path) throws IOException {
        super(path);
    }

    public Stream<LogFileSegment> logFiles() {
        if ( segments == null) {
            if ( isPlainText() || isDirectory())
                findSegments();
            else if ( isZip())
                findZIPSegments();
            else {
                LOG.warning("unknown log file format");
                segments = new ArrayList<>();
            }
        }
        return segments.stream();
    }

    private void findZIPSegments() {
        segments = discoverZIPSegments();
        orderSegments();
    }

    private List<LogFileSegment> discoverZIPSegments() {
        try (var zipfile = new ZipFile(getPath().toFile())) {
            return zipfile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(ZipEntry::getName)
                    .map(name -> new GCLogFileZipSegment(getPath(),name))
                    .collect(toList());
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Return the number of files. Useful if the file is a compressed file which may
     * contain multiple entries.
     * @return The number of files in the file.
     */
    public int getNumberOfFiles() {
        if ( this.segments == null)
            if ( isZip())
                findZIPSegments();
            else
                findSegments();
            return this.segments.size();
    }

    /**
     * Return the combined size, in bytes, of every rotating log segment that can be
     * discovered from the path this meta-data was created with. This includes the log
     * currently being written to. For a Zip file, the sum is of the uncompressed sizes
     * of all of the (non-directory) entries.
     *
     * @return The total number of bytes held in the log segments, or {@code 0L} if
     * there are no eligible segments.
     */
    public long getTotalByteSize() {
        if (isZip())
            return totalZipEntrySize();
        if (isPlainText() || isDirectory())
            return discoverSegments().stream()
                    .map(LogFileSegment::getPath)
                    .mapToLong(RotatingLogFileMetadata::sizeOf)
                    .sum();
        LOG.warning("unknown log file format");
        return 0L;
    }

    private static long sizeOf(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to determine the size of " + path, ioe);
            return 0L;
        }
    }

    private long totalZipEntrySize() {
        try (var zipfile = new ZipFile(getPath().toFile())) {
            return zipfile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .mapToLong(zipEntry -> uncompressedSize(zipfile, zipEntry))
                    .sum();
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return 0L;
    }

    /**
     * The uncompressed size is normally recorded in the Zip central directory. When it
     * isn't, fall back to inflating the entry and counting the bytes.
     */
    private static long uncompressedSize(ZipFile zipfile, ZipEntry zipEntry) {
        long size = zipEntry.getSize();
        if (size >= 0L)
            return size;
        try (var input = zipfile.getInputStream(zipEntry)) {
            long count = 0L;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > -1)
                count += read;
            return count;
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Unable to determine the size of " + zipEntry.getName(), ioe);
            return 0L;
        }
    }

    /**
     * Root for the pattern for the file currently being written to... has
     * a .<number> suffix for unified
     * a .current suffix for pre-unified.
     *
     * The possible parameters here along with the actions
     * 1) directory
     * 2) the file currently being written to
     * 3) a file not currently being written to.
     *
     * In all cases we want to find the file currently being written to and
     * use that to reverse engineer the root.
     *
     * @return String representing the pattern for the root of the rotating log name
     */
    private String getRootPattern() {

        // at this point we only have the path, not a segment... it maybe that we have to save the chosen segment
        // so  that we can normalize the code path for zip and file based logs????
        String[] bits;
        if (isDirectory()) {
            // if base is gc.log, filter out gc.log.<number>
            bits = segments.stream()
                    .filter(segment -> !segment.getSegmentName().matches(".+\\.\\d+$"))
                    .findFirst()
                    .get()
                    .getSegmentName().split("\\.");
        } else if ( isZip()) {
            bits = segments.get(0).getSegmentName().split("\\.");
        } else {
            bits = getPath().getFileName().toString().split("\\.");
        }

        int baseLength = 0;
        if ( "current".equals(bits[bits.length - 1]))
            baseLength = bits.length - 2;
        else if ( bits[bits.length - 1].matches("\\d+$"))
            baseLength = bits.length - 1;
        else
            baseLength = bits.length;

        StringBuilder base = new StringBuilder(bits[0]);
        for ( int i = 1; i < baseLength; i++)
            base.append(".").append(bits[i]);
        return base.toString();
    }

    private void findSegments() {
        segments = discoverSegments();
        orderSegments();
    }

    private List<LogFileSegment> discoverSegments() {
        List<LogFileSegment> discovered = new ArrayList<>();
        try {
            if (isDirectory()) {
                Files.list(getPath()).map(GCLogFileSegment::new).forEach(discovered::add);
            }
            else {
                Files.list(getPath().getParent())
                        .filter(file -> file.getFileName().toString().startsWith(getRootPattern()))
                        .map(p -> new GCLogFileSegment(p)).forEach(discovered::add);
            }
        } catch (IOException ioe) {
            LOG.log(Level.WARNING,"Unable to find log segments.", ioe);
        }
        return discovered;
    }

    private void orderSegments() {

        if (segments.size() < 2) return;

        LinkedList<LogFileSegment> orderedList = new LinkedList<>();
        List<LogFileSegment> workingList = new ArrayList<>();
        workingList.addAll(segments);

        // Find current
        String basePattern = getRootPattern();
        LogFileSegment current = workingList.stream()
                .filter( segment -> segment.getSegmentName().endsWith(basePattern) || segment.getSegmentName().endsWith(".current"))
                .findFirst().get();

        orderedList.addLast(current);
        workingList = removeIneligibleSegments (workingList, current);
        while ( ! workingList.isEmpty()) {
            current = workingList.stream()
                    .max(Comparator.comparing(LogFileSegment::getEndTime))
                    .get();
            orderedList.addFirst(current);
            workingList = removeIneligibleSegments (workingList, current);
        }
        segments = orderedList;
    }

    private List<LogFileSegment> removeIneligibleSegments(final List<LogFileSegment> logFileSegments, final LogFileSegment current) {
        return logFileSegments.stream()
                .filter( segment -> segment.getEndTime() <= current.getStartTime())
                .collect(toList());
    }
}
