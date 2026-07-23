// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.io.core.FileFormat;
import com.microsoft.gctoolkit.io.core.GCLogSourceUtil;
import com.microsoft.gctoolkit.jvm.Diary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SafepointLogFile implements DataSource<String> {

    private final Path path;
    private FileFormat format;

    public SafepointLogFile(Path path) {
        this.path = path;
    }

    /**
     * todo: for the moment this diary is empty.
     * @return a diary
     */
    public Diary diary() {
        return new Diary();
    }

    @Override
    public String endOfData() {
        return GCLogFile.END_OF_DATA_SENTINEL;
    }

    public Path getPath() { return path; }

    private FileFormat getFormat() {
        if (format == null) {
            format = GCLogSourceUtil.detectFormat(path);
        }
        return format;
    }

    public Stream<String> stream() throws IOException {
        FileFormat fmt = getFormat();
        if (fmt == FileFormat.PLAINTEXT) {
            return GCLogSourceUtil.streamPlainText(path);
        } else if (fmt == FileFormat.ZIP) {
            return GCLogSourceUtil.streamZipFile(path);
        } else if (fmt == FileFormat.GZIP) {
            return GCLogSourceUtil.streamGZipFile(path);
        }
        throw new IOException("Unable to read " + path);
    }

}
