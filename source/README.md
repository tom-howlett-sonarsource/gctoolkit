# Shared log sources

`gctoolkit-source` (`com.microsoft.gctoolkit.source`) provides production IO
utilities independent of the API and parser. `LogSource` discovers formats by
magic bytes, lists directory paths and non-directory ZIP entries, reports physical
byte sizes, and opens plain, GZIP, first-file ZIP, or named ZIP-entry line streams.
`allZipLines` concatenates the raw ZIP entry streams in archive order.

Callers own returned streams and must close them. Closing a compressed line
stream closes its reader and underlying archive. Plain files use UTF-8, while
compressed files retain the existing platform-default decoding. API-specific
line trimming, blank filtering, sentinels, and rotating-log ordering remain in
the API module; safepoint sources return unmodified lines.
