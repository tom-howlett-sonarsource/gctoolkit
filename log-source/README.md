# Shared log source IO

`gctoolkit-log-source` is a production dependency of both `gctoolkit-api`
and `gctoolkit-parser`. Its JPMS name is `com.microsoft.gctoolkit.logsource`.
It has no dependency on either consumer.

`LogSource` provides content-based format detection, directory and ZIP-entry
discovery, file byte sizing, and plain, GZIP, and ZIP line streams. Discovery
retains filesystem/archive order; log rotation ordering remains in the API.
File byte sizing measures the source on disk, not decompressed content.

Single ZIP streams skip directory entries and read only the first file.
Named-entry streams read a specified ZIP member. The all-entry utility concatenates
file-entry bytes in archive order, without inserting separators. The API's public
rotating source continues to stream named entries in log rotation order.
Plain files retain `Files.lines` UTF-8 decoding; compressed streams retain the
existing default-charset decoding.

Callers must close returned streams, including after partial consumption, to
release readers and archive handles. Filtering, trimming, and end-of-data
sentinels remain the responsibility of API/parser callers.
