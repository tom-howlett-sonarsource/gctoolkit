# Shared log source IO

`gctoolkit-log-source` (`com.microsoft.gctoolkit.logsource` in JPMS) supplies
filesystem discovery, on-disk byte sizing, magic-byte format detection, and
plain, GZIP, first-file ZIP, and named ZIP-entry line streams to the API and
parser modules. It has no dependency on either consumer.

Callers own the returned streams and must close them. Discovery returns a
materialized list and closes its directory or ZIP handle before returning.
Plain text retains UTF-8 decoding from `Files.lines`; compressed sources retain
the platform-default decoding used by the existing readers. Filtering, sentinel
insertion, segment selection, and chronological ordering remain in the API.
