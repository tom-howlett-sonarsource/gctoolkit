package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipFile;

@FunctionalInterface
interface ZipFileOpener {

    ZipFileOpener DEFAULT = path -> new ZipFile(path.toFile());

    ZipFile open(Path path) throws IOException;
}
