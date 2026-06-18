// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.source.GCLogSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

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
            if (isPlainText() || isDirectory()) {
                findSegments();
            } else if (isZip()) {
                findZIPSegments();
            } else {
                LOG.warning("unknown log file format");
                segments = new ArrayList<>();
            }
        }
        return segments.stream();
    }

    private void findZIPSegments() {
        try {
            segments = GCLogSource.zipEntryNames(getPath()).stream()
                    .map(name -> new GCLogFileZipSegment(getPath(), name))
                    .collect(toList());
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        orderSegments();
    }

    /**
     * Return the number of files. Useful if the file is a compressed file which may
     * contain multiple entries.
     * @return The number of files in the file.
     */
    public int getNumberOfFiles() {
        if (this.segments == null) {
            if (isZip()) {
                findZIPSegments();
            } else {
                findSegments();
            }
        }
        return this.segments.size();
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
        return GCLogSource.rootPattern(segmentNames(), getPath(), getFileFormat());
    }

    private void findSegments() {
        segments = new ArrayList<>();
        try {
            GCLogSource.discoverSegments(getPath(), getFileFormat(), getRootPattern()).stream()
                    .map(GCLogFileSegment::new)
                    .forEach(segments::add);
        } catch (IOException ioe) {
            LOG.log(Level.WARNING,"Unable to find log segments.", ioe);
        }
        orderSegments();
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
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No current log segment found"));

        orderedList.addLast(current);
        workingList = removeIneligibleSegments (workingList, current);
        while ( ! workingList.isEmpty()) {
            current = workingList.stream()
                    .max(Comparator.comparing(LogFileSegment::getEndTime))
                    .orElseThrow(() -> new IllegalStateException("No eligible log segment found"));
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

    private List<String> segmentNames() {
        return segments.stream()
                .map(LogFileSegment::getSegmentName)
                .collect(toList());
    }
}
