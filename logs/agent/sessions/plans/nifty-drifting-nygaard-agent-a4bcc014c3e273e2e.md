# Implementation Plan: New `logsource` Shared Module for GCToolKit

## 1. Overview

### Problem Statement
Stream-opening code (ZIP, GZIP, plain text) and file-format detection logic (magic-byte reading) are duplicated between the `api` module (`SingleGCLogFile`, `LogFileMetadata`) and the `parser` module (`SafepointLogFile`). The parser copy also contains a critical null-check bug in `streamZipFile()` and a guaranteed NPE from `private final LogFileMetadata metadata = null;`.

### Solution
Extract the shared file-format detection and stream-opening logic into a new `logsource` module (`com.microsoft.gctoolkit.logsource`). Both `api` and `parser` will depend on it. The existing `LogFileMetadata` will delegate to the new module's utilities while preserving its public API. `SafepointLogFile` will be fixed to use the new utilities directly, eliminating its bugs.

### What IS Extracted
- **File format detection**: magic-byte reading, format classification
- **Single-entry stream opening**: plain text via `Files.lines()`, ZIP first-entry via `ZipInputStream`, GZIP via `GZIPInputStream`
- **File format enum**: public version of the currently package-private `FileFormat`

### What is NOT Extracted
- `RotatingGCLogFile.streamZipFile()` -- uses `ZipFile` + `SequenceInputStream` for multi-entry concatenation (different algorithm)
- `GCLogFileZipSegment.stream()` -- opens a specific named ZIP entry (different use case)
- `LogFileMetadata` abstract class hierarchy -- deeply tied to api domain classes
- Segment classes, rotating log discovery -- api-specific domain logic
- Tail reading methods -- out of scope
- `TestLogFile` helpers -- explicitly excluded

---

## 2. Module Structure

### 2.1 Directory Layout

```
logsource/
  pom.xml
  src/
    main/
      java/
        module-info.java
        com/
          microsoft/
            gctoolkit/
              logsource/
                FileFormat.java
                FileFormatDetector.java
                LogSourceStreams.java
    test/
      java/
        com/
          microsoft/
            gctoolkit/
              logsource/
                FileFormatTest.java
                FileFormatDetectorTest.java
                LogSourceStreamsTest.java
      resources/
        (test resource files -- see Section 7)
```

### 2.2 Maven Artifact Naming

Following the project convention (all artifacts are `gctoolkit-<name>`):
- **artifactId**: `gctoolkit-logsource`
- **groupId**: `com.microsoft.gctoolkit` (inherited from parent)
- **version**: `${project.version}` / `3.7.1-SNAPSHOT` (inherited)

### 2.3 JPMS Module Name

Following the convention (`com.microsoft.gctoolkit.<name>`):
- **module name**: `com.microsoft.gctoolkit.logsource`

---

## 3. Detailed File Specifications

### 3.1 `/app/logsource/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.microsoft.gctoolkit</groupId>
        <artifactId>gctoolkit</artifactId>
        <version>3.7.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>gctoolkit-logsource</artifactId>
    <name>GCToolKit LogSource</name>
    <description>Shared file-format detection and stream-opening utilities for GC log sources.</description>
    <url>${project.parent.url}</url>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Key points:
- No production dependencies beyond the JDK -- this module is self-contained.
- Same parent POM pattern as `api`, `parser`, `vertx`.
- JUnit 5 test dependencies from parent `dependencyManagement`.
- No special build plugins needed (inherits `jacoco`, `spotbugs`, `enforcer` from parent).

### 3.2 `/app/logsource/src/main/java/module-info.java`

```java
// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared file-format detection and stream-opening utilities for GC log sources.
 */
module com.microsoft.gctoolkit.logsource {
    exports com.microsoft.gctoolkit.logsource;
}
```

Key points:
- No `requires` directives needed -- only uses `java.base` types (`java.io`, `java.nio.file`, `java.util.zip`, `java.util.stream`).
- No `java.logging` requirement since the new classes should not log (detection is deterministic, callers handle errors).
- Exports the single package unconditionally (both `api` and `parser` need access).

### 3.3 `/app/logsource/src/main/java/com/microsoft/gctoolkit/logsource/FileFormat.java`

```java
// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * Identifies the format of a log file based on its content or filesystem type.
 */
public enum FileFormat {
    /** ZIP-compressed archive (PK magic bytes 0x50 0x4B). */
    ZIP,
    /** GZIP-compressed file (magic bytes 0x1F 0x8B). */
    GZIP,
    /** Uncompressed plain text file. */
    PLAINTEXT,
    /** A filesystem directory. */
    DIRECTORY,
    /** Format could not be determined. */
    UNKNOWN
}
```

This is a public version of the currently package-private `LogFileMetadata.FileFormat` enum. Same values, same order.

### 3.4 `/app/logsource/src/main/java/com/microsoft/gctoolkit/logsource/FileFormatDetector.java`

```java
// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Detects the format of a file by reading its magic bytes.
 * <p>
 * Directories are identified by filesystem metadata.
 * ZIP and GZIP are identified by their 2-byte magic headers.
 * Files that are neither directories nor recognized archives are
 * classified as {@link FileFormat#PLAINTEXT}.
 */
public final class FileFormatDetector {

    /** First byte of GZIP magic number. */
    public static final int GZIP_MAGIC1 = 0x1F;
    /** Second byte of GZIP magic number. */
    public static final int GZIP_MAGIC2 = 0x8B;

    /** First byte of ZIP magic number (PK). */
    public static final int ZIP_MAGIC1 = 0x50;
    /** Second byte of ZIP magic number (PK). */
    public static final int ZIP_MAGIC2 = 0x4B;

    private FileFormatDetector() {
        // utility class -- no instances
    }

    /**
     * Detect the file format of the file at the given path.
     *
     * @param path the file or directory to inspect
     * @return the detected {@link FileFormat}, never {@code null}
     * @throws IOException if the file cannot be read
     */
    public static FileFormat detect(Path path) throws IOException {
        if (path.toFile().isDirectory()) {
            return FileFormat.DIRECTORY;
        }
        if (matchesMagic(path, GZIP_MAGIC1, GZIP_MAGIC2)) {
            return FileFormat.GZIP;
        }
        if (matchesMagic(path, ZIP_MAGIC1, ZIP_MAGIC2)) {
            return FileFormat.ZIP;
        }
        return FileFormat.PLAINTEXT;
    }

    /**
     * Test whether the first two bytes of the file match the given values.
     *
     * @param path   the file to read
     * @param magic1 expected first byte
     * @param magic2 expected second byte
     * @return {@code true} if the first two bytes match
     * @throws IOException if the file cannot be read
     */
    public static boolean matchesMagic(Path path, int magic1, int magic2) throws IOException {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            int b1 = fis.read();
            int b2 = fis.read();
            return b1 == magic1 && b2 == magic2;
        }
    }
}
```

Design decisions:
- **`detect()` throws `IOException`** instead of silently returning UNKNOWN. The original `LogFileMetadata.magic(int,int)` swallows IOExceptions, which masks real problems. The new method lets callers decide how to handle errors. `LogFileMetadata` will catch the IOException and fall back to UNKNOWN to preserve its existing behavior.
- **`matchesMagic()` is public** so `LogFileMetadata` can delegate its existing `boolean magic(int, int)` method to it.
- **Class is `final` with private constructor** -- pure utility class pattern.
- **Uses `FileInputStream`** to match the existing `LogFileMetadata.magic()` implementation exactly (same I/O path, same byte-reading approach).

### 3.5 `/app/logsource/src/main/java/com/microsoft/gctoolkit/logsource/LogSourceStreams.java`

```java
// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Static utility methods for opening a single log file as a {@code Stream<String>}.
 * <p>
 * Supports plain text, single-entry ZIP, and GZIP files.
 * For multi-entry ZIP archives or named ZIP entries, use the
 * specialized stream methods in the api module.
 */
public final class LogSourceStreams {

    private LogSourceStreams() {
        // utility class -- no instances
    }

    /**
     * Open a plain text file as a stream of lines.
     *
     * @param path the path to the plain text file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamPlainTextFile(Path path) throws IOException {
        return Files.lines(path);
    }

    /**
     * Open the first non-directory entry in a ZIP file as a stream of lines.
     * <p>
     * This method is designed for single-entry ZIP archives. For multi-entry
     * archives, use the specialized methods in {@code RotatingGCLogFile}.
     *
     * @param path the path to the ZIP file
     * @return a stream of lines from the first non-directory entry
     * @throws IOException if the file cannot be read or contains no non-directory entries
     */
    public static Stream<String> streamZipFile(Path path) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(path));
        ZipEntry entry;
        do {
            entry = zipStream.getNextEntry();
        } while (entry != null && entry.isDirectory());
        if (entry == null) {
            zipStream.close();
            throw new IOException("ZIP file contains no non-directory entries: " + path);
        }
        return new BufferedReader(
                new InputStreamReader(new BufferedInputStream(zipStream))).lines();
    }

    /**
     * Open a GZIP-compressed file as a stream of lines.
     *
     * @param path the path to the GZIP file
     * @return a stream of lines
     * @throws IOException if the file cannot be read
     */
    public static Stream<String> streamGZipFile(Path path) throws IOException {
        GZIPInputStream gzipStream = new GZIPInputStream(Files.newInputStream(path));
        return new BufferedReader(
                new InputStreamReader(new BufferedInputStream(gzipStream))).lines();
    }

    /**
     * Open a file as a stream of lines, auto-detecting the format.
     * <p>
     * Directories and unknown formats cause an {@link IOException}.
     *
     * @param path the path to the file
     * @return a stream of lines
     * @throws IOException if the file cannot be read or the format is unsupported
     */
    public static Stream<String> stream(Path path) throws IOException {
        FileFormat format = FileFormatDetector.detect(path);
        switch (format) {
            case PLAINTEXT:
                return streamPlainTextFile(path);
            case ZIP:
                return streamZipFile(path);
            case GZIP:
                return streamGZipFile(path);
            default:
                throw new IOException("Unable to stream file of format " + format + ": " + path);
        }
    }
}
```

Design decisions:
- **`streamZipFile` includes the null check** -- this is the corrected version that both `SingleGCLogFile` (already had it) and `SafepointLogFile` (was missing it) will now share.
- **Added `entry == null` check with explicit close and error** -- improvement over original which would return a stream backed by a ZipInputStream with no current entry. Now throws a descriptive IOException.
- **Added convenience `stream(Path)` method** -- auto-detects format and dispatches, which will simplify `SafepointLogFile` greatly.
- **No logging** -- callers (in `api` and `parser`) handle errors. Keeps the module dependency-free.

---

## 4. Root POM Changes

### 4.1 Add to `<modules>` -- `/app/pom.xml` line ~90

Insert `logsource` BEFORE `api` and `parser` in the module list:

```xml
<modules>
    <module>gclogs</module>
    <module>logsource</module>   <!-- NEW: must precede api and parser -->
    <module>api</module>
    <module>parser</module>
    <module>vertx</module>
    <module>sample</module>
    <module>IT</module>
</modules>
```

### 4.2 Add to `<dependencyManagement>` -- `/app/pom.xml` line ~108

Add a managed dependency entry (alphabetical within the `com.microsoft.gctoolkit` group):

```xml
<dependency>
    <groupId>com.microsoft.gctoolkit</groupId>
    <artifactId>gctoolkit-logsource</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 5. Consumer Module Changes

### 5.1 API Module

#### 5.1.1 `/app/api/pom.xml` -- Add dependency

Add before the JUnit dependencies:

```xml
<dependency>
    <groupId>com.microsoft.gctoolkit</groupId>
    <artifactId>gctoolkit-logsource</artifactId>
</dependency>
```

#### 5.1.2 `/app/api/src/main/java/module-info.java` -- Add requires

Add `requires com.microsoft.gctoolkit.logsource;` after the existing `requires java.logging;` line (line 33):

```java
module com.microsoft.gctoolkit.api {
    requires java.logging;
    requires com.microsoft.gctoolkit.logsource;    // NEW
    // ... rest unchanged
}
```

#### 5.1.3 `/app/api/src/main/java/com/microsoft/gctoolkit/io/LogFileMetadata.java` -- Delegate to `FileFormatDetector`

Changes required:

1. **Add import**: `import com.microsoft.gctoolkit.logsource.FileFormatDetector;`

2. **Remove the magic-byte constants** (lines 18-22). They are now in `FileFormatDetector`.

3. **Remove the inner `FileFormat` enum** (lines 99-105). Now in `com.microsoft.gctoolkit.logsource.FileFormat`.

4. **Add import**: `import com.microsoft.gctoolkit.logsource.FileFormat;` (same enum name, but now the public one from logsource).

5. **Rewrite `magic(int, int)` method** (lines 36-45) to delegate:

```java
boolean magic(int field1, int field2) {
    try {
        return FileFormatDetector.matchesMagic(path, field1, field2);
    } catch (IOException ioe) {
        LOG.warning(ioe.getMessage());
    }
    return false;
}
```

Note: This preserves the exact existing behavior (swallow IOException, return false). The method signature stays `boolean magic(int field1, int field2)` which is package-private, so no public API change.

6. **Rewrite `magic()` method** (lines 49-57) to delegate:

```java
private void magic() {
    try {
        fileFormat = FileFormatDetector.detect(path);
    } catch (IOException ioe) {
        LOG.warning(ioe.getMessage());
        fileFormat = FileFormat.UNKNOWN;
    }
}
```

This preserves the existing behavior while delegating the core logic.

7. **Keep all public query methods** (`isZip()`, `isGZip()`, `isPlainText()`, `isDirectory()`) exactly as-is. They still compare against `FileFormat` enum values, which are the same names.

**Important**: The field `private FileFormat fileFormat = FileFormat.UNKNOWN;` on line 24 stays the same -- the type reference `FileFormat` now resolves to `com.microsoft.gctoolkit.logsource.FileFormat` via the new import. The boolean comparisons in `isZip()` etc. continue to work identically.

#### 5.1.4 `/app/api/src/main/java/com/microsoft/gctoolkit/io/SingleGCLogFile.java` -- Delegate to `LogSourceStreams`

Changes required:

1. **Add import**: `import com.microsoft.gctoolkit.logsource.LogSourceStreams;`

2. **Remove imports** that are no longer directly needed:
   - `java.io.BufferedInputStream`
   - `java.io.BufferedReader`
   - `java.io.InputStreamReader`
   - `java.util.zip.GZIPInputStream`
   - `java.util.zip.ZipEntry`
   - `java.util.zip.ZipInputStream`

3. **Replace `streamZipFile` method** (lines 70-77):

```java
private static Stream<String> streamZipFile(Path path) throws IOException {
    return LogSourceStreams.streamZipFile(path);
}
```

4. **Replace `streamGZipFile` method** (lines 79-82):

```java
private static Stream<String> streamGZipFile(Path path) throws IOException {
    return LogSourceStreams.streamGZipFile(path);
}
```

Alternative (more aggressive refactoring): Remove these two private methods entirely and call `LogSourceStreams` directly in `stream(LogFileMetadata)`:

```java
private Stream<String> stream(LogFileMetadata metadata) throws IOException {
    Stream<String> stream = null;
    if (metadata.isPlainText()) {
        stream = LogSourceStreams.streamPlainTextFile(metadata.getPath());
    } else if (metadata.isZip()) {
        stream = LogSourceStreams.streamZipFile(metadata.getPath());
    } else if (metadata.isGZip()) {
        stream = LogSourceStreams.streamGZipFile(metadata.getPath());
    }
    if (stream == null)
        throw new IOException("Unable to read " + path.toString());
    return Stream.concat(stream
            .filter(Objects::nonNull)
            .filter(line -> ! line.isBlank())
            .map(String::trim)
            .filter(s -> s.length() > 0)
            , Stream.of(endOfData()));
}
```

**Recommended approach**: Use the second (more aggressive) approach -- remove the private wrapper methods entirely and inline the `LogSourceStreams` calls. This eliminates unnecessary indirection and makes the delegation transparent.

### 5.2 Parser Module

#### 5.2.1 `/app/parser/pom.xml` -- Add dependency

Add before JUnit dependencies, after the `gctoolkit-api` dependency:

```xml
<dependency>
    <groupId>com.microsoft.gctoolkit</groupId>
    <artifactId>gctoolkit-logsource</artifactId>
</dependency>
```

Note: The parser already depends on `gctoolkit-api` which will transitively pull in logsource via Maven (but NOT via JPMS -- JPMS requires explicit `requires`). Adding the explicit dependency makes the build intent clear.

#### 5.2.2 `/app/parser/src/main/java/module-info.java` -- Add requires

Add `requires com.microsoft.gctoolkit.logsource;` after the existing `requires` directives:

```java
module com.microsoft.gctoolkit.parser {
    requires com.microsoft.gctoolkit.api;
    requires com.microsoft.gctoolkit.logsource;    // NEW
    requires java.logging;
    // ... rest unchanged
}
```

#### 5.2.3 `/app/parser/src/main/java/com/microsoft/gctoolkit/parser/io/SafepointLogFile.java` -- Full Rewrite

This file needs substantial changes to fix the bugs and use the new utilities:

```java
// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.DataSource;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.logsource.LogSourceStreams;
import com.microsoft.gctoolkit.jvm.Diary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SafepointLogFile implements DataSource<String> {

    private final Path path;

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

    public Stream<String> stream() throws IOException {
        return LogSourceStreams.stream(path);
    }
}
```

Changes summary:
1. **Removed `private final LogFileMetadata metadata = null;`** -- this was the NPE bug. The field was always null, so `metadata.isPlainText()` etc. always threw NPE.
2. **Removed all six imports** for `BufferedInputStream`, `BufferedReader`, `InputStreamReader`, `Files`, `GZIPInputStream`, `ZipEntry`, `ZipInputStream`.
3. **Removed import** of `LogFileMetadata` -- no longer needed.
4. **Added import** of `LogSourceStreams`.
5. **Replaced `stream()` body** with a single call to `LogSourceStreams.stream(path)` which auto-detects format.
6. **Removed `streamZipFile()` and `streamGZipFile()` methods** entirely -- delegated to `LogSourceStreams`.

---

## 6. Dependency Graph After Changes

```
logsource (new, no dependencies)
    ^          ^
    |          |
   api      parser
    ^          ^
    |          |
   +-----------+
    |     |    |
  vertx sample IT
```

Build order in root POM: `gclogs -> logsource -> api -> parser -> vertx -> sample -> IT`

---

## 7. Test Plan (Red-Green, 80%+ Coverage)

### 7.1 Test Resources for the New Module

The logsource module needs small test files. Create minimal test resources:

**`/app/logsource/src/test/resources/`**:
- `plain.log` -- a few lines of plain text (e.g., 3 lines of "line 1\nline 2\nline 3")
- `compressed.log.gz` -- the same content gzip-compressed
- `compressed.log.zip` -- the same content in a zip archive (single entry)
- `empty.zip` -- an empty zip file (or zip with only directory entries) for edge case testing
- `directory/` -- a subdirectory for directory detection testing

These must be created programmatically in test setup or committed as small binary resources. For the zip/gzip files, consider using `@BeforeAll` to create them from plain text in a temp directory, which avoids committing binary files. Use `@TempDir` (JUnit 5).

### 7.2 `FileFormatTest.java`

Location: `/app/logsource/src/test/java/com/microsoft/gctoolkit/logsource/FileFormatTest.java`

Tests:
- Verify all five enum values exist: `ZIP`, `GZIP`, `PLAINTEXT`, `DIRECTORY`, `UNKNOWN`.
- Verify `valueOf()` round-trips for each constant.
- Verify `values().length == 5`.

This is a simple enum smoke test for coverage.

### 7.3 `FileFormatDetectorTest.java`

Location: `/app/logsource/src/test/java/com/microsoft/gctoolkit/logsource/FileFormatDetectorTest.java`

Tests (all using `@TempDir` to create test files):

1. **`detect_plainTextFile_returnsPLAINTEXT`** -- Write a small text file, verify `detect()` returns `PLAINTEXT`.
2. **`detect_gzipFile_returnsGZIP`** -- Create a GZIP file programmatically using `GZIPOutputStream`, verify `detect()` returns `GZIP`.
3. **`detect_zipFile_returnsZIP`** -- Create a ZIP file using `ZipOutputStream`, verify `detect()` returns `ZIP`.
4. **`detect_directory_returnsDIRECTORY`** -- Create a subdirectory, verify `detect()` returns `DIRECTORY`.
5. **`matchesMagic_matchingBytes_returnsTrue`** -- Write bytes `{0x50, 0x4B, ...}`, verify `matchesMagic(path, 0x50, 0x4B)` returns true.
6. **`matchesMagic_nonMatchingBytes_returnsFalse`** -- Write different bytes, verify returns false.
7. **`detect_nonexistentFile_throwsIOException`** -- Pass a path that does not exist, verify IOException.
8. **`detect_emptyFile_returnsPLAINTEXT`** -- Create an empty file (0 bytes), verify behavior. (Magic bytes will be -1, so no format matches -- returns PLAINTEXT.)

### 7.4 `LogSourceStreamsTest.java`

Location: `/app/logsource/src/test/java/com/microsoft/gctoolkit/logsource/LogSourceStreamsTest.java`

Tests (all using `@TempDir`):

1. **`streamPlainTextFile_returnsAllLines`** -- Write 3 lines, stream, verify count and content.
2. **`streamGZipFile_returnsAllLines`** -- Create GZIP file with known content, stream, verify.
3. **`streamZipFile_singleEntry_returnsAllLines`** -- Create ZIP with one file entry, stream, verify.
4. **`streamZipFile_withDirectoryEntries_skipsDirectories`** -- Create ZIP with a directory entry followed by a file entry, verify the file content is returned.
5. **`streamZipFile_emptyZip_throwsIOException`** -- Create a ZIP with no non-directory entries, verify IOException with descriptive message.
6. **`stream_autoDetect_plainText`** -- Use `stream(path)` on a plain text file, verify.
7. **`stream_autoDetect_gzip`** -- Use `stream(path)` on a GZIP file, verify.
8. **`stream_autoDetect_zip`** -- Use `stream(path)` on a ZIP file, verify.
9. **`stream_directory_throwsIOException`** -- Use `stream(path)` on a directory, verify IOException.

Coverage goal: These 9 tests cover all branches in `LogSourceStreams` (all three format-specific methods + the switch/default in `stream()` + the null entry edge case in `streamZipFile()`). Combined with the 8 `FileFormatDetector` tests and 3 `FileFormat` tests, this should well exceed 80% line and branch coverage on the new module's production code.

### 7.5 Existing Tests -- Verification

After making the changes, the following existing tests must continue to pass unchanged:

- `/app/IT/src/test/java/com/microsoft/gctoolkit/integration/io/SingleGarbageCollectionLogFileTest.java` -- Tests `SingleGCLogFile` streaming of plain, zip, and gzip files. This validates the delegation works.
- `/app/api/src/test/java/com/microsoft/gctoolkit/io/RotatingGCLogTest.java` -- Tests rotating logs (NOT affected by this change, but verifies no regressions).
- All parser tests -- verify no regressions from the `module-info.java` change.

No existing test should require changes because the public API of `LogFileMetadata`, `SingleGCLogFile`, and `SafepointLogFile` is preserved.

---

## 8. Step-by-Step Implementation Sequence

### Phase 1: Create the new module (RED tests first)

**Step 1.1**: Create directory structure:
```
mkdir -p /app/logsource/src/main/java/com/microsoft/gctoolkit/logsource
mkdir -p /app/logsource/src/test/java/com/microsoft/gctoolkit/logsource
mkdir -p /app/logsource/src/test/resources
```

**Step 1.2**: Create `/app/logsource/pom.xml` (as specified in Section 3.1).

**Step 1.3**: Write the three test classes FIRST (red phase):
- `FileFormatTest.java`
- `FileFormatDetectorTest.java`
- `LogSourceStreamsTest.java`

These tests will fail because the production classes do not yet exist.

**Step 1.4**: Create production classes (green phase):
- `FileFormat.java` (Section 3.3)
- `FileFormatDetector.java` (Section 3.4)
- `LogSourceStreams.java` (Section 3.5)

**Step 1.5**: Create `module-info.java` for the new module (Section 3.2).

**Step 1.6**: Update root POM:
- Add `<module>logsource</module>` to `<modules>` list (before `api`)
- Add `gctoolkit-logsource` to `<dependencyManagement>`

**Step 1.7**: Build and verify the new module in isolation:
```
./mvnw -pl logsource clean verify
```
All tests should pass. Verify JaCoCo coverage > 80%.

### Phase 2: Update the API module

**Step 2.1**: Add `gctoolkit-logsource` dependency to `/app/api/pom.xml`.

**Step 2.2**: Add `requires com.microsoft.gctoolkit.logsource;` to `/app/api/src/main/java/module-info.java`.

**Step 2.3**: Refactor `/app/api/src/main/java/com/microsoft/gctoolkit/io/LogFileMetadata.java`:
- Add imports for `FileFormatDetector` and `FileFormat` from logsource
- Remove the inner `FileFormat` enum
- Remove the magic-byte constants
- Delegate `magic()` and `magic(int,int)` to `FileFormatDetector`

**Step 2.4**: Refactor `/app/api/src/main/java/com/microsoft/gctoolkit/io/SingleGCLogFile.java`:
- Remove `streamZipFile` and `streamGZipFile` private methods
- Call `LogSourceStreams` directly in `stream(LogFileMetadata)`
- Remove unused imports

**Step 2.5**: Build the api module:
```
cd /app && ./mvnw -pl logsource,api clean verify
```
Existing api tests must pass.

### Phase 3: Update the Parser module

**Step 3.1**: Add `gctoolkit-logsource` dependency to `/app/parser/pom.xml`.

**Step 3.2**: Add `requires com.microsoft.gctoolkit.logsource;` to `/app/parser/src/main/java/module-info.java`.

**Step 3.3**: Rewrite `/app/parser/src/main/java/com/microsoft/gctoolkit/parser/io/SafepointLogFile.java` as specified in Section 5.2.3. This fixes:
- The `private final LogFileMetadata metadata = null;` NPE bug
- The missing null check in `streamZipFile()`

**Step 3.4**: Build the parser module:
```
cd /app && ./mvnw -pl logsource,api,parser clean verify
```
All parser tests must pass.

### Phase 4: Full build and integration verification

**Step 4.1**: Full project build:
```
cd /app && ./mvnw clean verify
```

**Step 4.2**: Verify JaCoCo coverage on logsource module exceeds 80%.

**Step 4.3**: Run integration tests (IT module):
```
cd /app && ./mvnw -pl IT verify
```

---

## 9. Risk Analysis and Edge Cases

### 9.1 JPMS Transitivity
The `api` module's `module-info.java` uses `requires` (not `requires transitive`) for the logsource module. This means that downstream modules (vertx, sample, IT) do NOT automatically see the logsource module. This is correct -- those modules should not need to use logsource directly. If any of them do need it in the future, they can add their own `requires`.

However, the parser module also needs `requires com.microsoft.gctoolkit.logsource` explicitly, even though it already depends on `api` via Maven. JPMS does not inherit transitive visibility unless `requires transitive` is used.

### 9.2 `LogFileMetadata.FileFormat` Package-Private Visibility
The old `FileFormat` enum was package-private (no access modifier on the `enum` inside `LogFileMetadata`). It was only used within the `LogFileMetadata` class itself and within the `com.microsoft.gctoolkit.io` package. Replacing it with the public `com.microsoft.gctoolkit.logsource.FileFormat` is safe because:
- No code outside the `io` package references the old enum by name
- The comparisons via `isZip()`, `isGZip()`, etc. remain unchanged
- The enum values have the same names

### 9.3 `LogFileMetadata.magic(int, int)` is Package-Private
This method is `boolean magic(int field1, int field2)` (no access modifier = package-private). It is only called by the class's own private `magic()` method. Delegating it to `FileFormatDetector.matchesMagic()` is safe. The method is kept for backward compatibility in case subclasses in the same package call it (e.g., future development).

### 9.4 SafepointLogFile is Unused
The `SafepointLogFile` class is not imported or instantiated anywhere in the codebase. It exists but is dead code. The fix is still valuable because:
- The class may be used in the future (it implements `DataSource<String>`)
- The fix removes a known NPE bug
- It demonstrates the logsource module's utility

### 9.5 Thread Safety
The new utility classes are stateless (`final` classes with only `static` methods). They are inherently thread-safe.

### 9.6 Resource Leaks
`LogSourceStreams.streamZipFile()` creates a `ZipInputStream` that wraps a `Files.newInputStream()`. The caller is responsible for closing the returned `Stream<String>`, which will close the underlying reader/stream. This matches the existing behavior in `SingleGCLogFile`. The improvement is the explicit close and IOException when no entry is found.

---

## 10. Files Changed Summary

### New Files (7 files)
| File | Purpose |
|------|---------|
| `/app/logsource/pom.xml` | Maven module descriptor |
| `/app/logsource/src/main/java/module-info.java` | JPMS module descriptor |
| `/app/logsource/src/main/java/com/microsoft/gctoolkit/logsource/FileFormat.java` | Public enum |
| `/app/logsource/src/main/java/com/microsoft/gctoolkit/logsource/FileFormatDetector.java` | Format detection utility |
| `/app/logsource/src/main/java/com/microsoft/gctoolkit/logsource/LogSourceStreams.java` | Stream-opening utility |
| `/app/logsource/src/test/java/com/microsoft/gctoolkit/logsource/FileFormatDetectorTest.java` | Tests |
| `/app/logsource/src/test/java/com/microsoft/gctoolkit/logsource/LogSourceStreamsTest.java` | Tests |

Optional: `FileFormatTest.java` for enum coverage.

### Modified Files (7 files)
| File | Change |
|------|--------|
| `/app/pom.xml` | Add `logsource` module + `dependencyManagement` entry |
| `/app/api/pom.xml` | Add `gctoolkit-logsource` dependency |
| `/app/api/src/main/java/module-info.java` | Add `requires com.microsoft.gctoolkit.logsource` |
| `/app/api/src/main/java/com/microsoft/gctoolkit/io/LogFileMetadata.java` | Delegate to `FileFormatDetector`, remove inner enum and constants |
| `/app/api/src/main/java/com/microsoft/gctoolkit/io/SingleGCLogFile.java` | Delegate to `LogSourceStreams`, remove duplicated methods |
| `/app/parser/pom.xml` | Add `gctoolkit-logsource` dependency |
| `/app/parser/src/main/java/module-info.java` | Add `requires com.microsoft.gctoolkit.logsource` |
| `/app/parser/src/main/java/com/microsoft/gctoolkit/parser/io/SafepointLogFile.java` | Full rewrite using `LogSourceStreams`, fixing NPE bug |
