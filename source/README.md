# GCToolKit Source

`gctoolkit-source` (`com.microsoft.gctoolkit.source` in JPMS) provides shared
production IO for the API and parser. It has no dependency on either module.

`LogSource` discovers directory children and ZIP entry names, reports stored
byte sizes, detects compression by magic bytes rather than filename extensions,
and opens plain, GZIP, first-entry ZIP, named-entry ZIP, or concatenated ZIP
line streams. Close returned streams with try-with-resources to release readers
and archive handles. Plain files use UTF-8 (`Files.lines`); compressed files use
the default charset, preserving existing reader behavior.

GC-specific trimming, blank-line filtering, sentinels, and rotation ordering
remain in their existing callers. Safepoint lines are returned unmodified.
