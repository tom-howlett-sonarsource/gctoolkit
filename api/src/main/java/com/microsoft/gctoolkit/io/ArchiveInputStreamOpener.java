package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@FunctionalInterface
interface ArchiveInputStreamOpener {

    ArchiveInputStreamOpener DEFAULT = Files::newInputStream;

    InputStream open(Path path) throws IOException;
}
