# GC log source utilities

`gctoolkit-source` is a dependency-free production module shared by the API and
parser. Its JPMS name is `com.microsoft.gctoolkit.source`.

`LogSource` discovers immediate directory children and ZIP entry names, measures
stored bytes (recursively for directories), detects formats by magic bytes, and
opens plain, first-entry ZIP, named-entry ZIP, and GZIP line streams. Callers own
the returned streams and must close them. Plain files use UTF-8, while compressed
streams retain the existing platform-default decoding. Filtering, sentinels,
rotating-log ordering, and GC timestamp interpretation remain in the callers.
