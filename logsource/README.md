# Shared log source IO

`gctoolkit-logsource` is the dependency-free production IO module shared by the
API and parser. Its JPMS name is `com.microsoft.gctoolkit.logsource`.

`LogSource` detects plain, ZIP, and GZIP sources by their leading bytes, lists
directory children and non-directory ZIP entries, reports on-disk byte sizes,
and opens raw line streams. ZIP source streams read the first non-directory
entry; named-entry streams support rotating archives. Callers must close all
returned streams, including when terminating consumption early.

Plain text uses UTF-8, while compressed streams retain the existing default
charset behavior. GC-specific filtering, sentinels, timestamps, and rotating
segment ordering remain in the API and parser rather than this module.
