# Shared log source IO

`gctoolkit-log-source` (`com.microsoft.gctoolkit.logsource` in JPMS) is a
production dependency of both the API and parser modules. It has no dependency
on either module.

`LogSource` detects plain, ZIP and GZIP sources by their content, lists directory
contents and non-directory ZIP entries, reports physical file sizes in bytes,
and opens line streams. Single-source ZIP reading selects the first
non-directory entry; named-entry reading supports rotating logs. Plain files use
UTF-8, while compressed streams retain the existing default-charset decoding.

Callers must close returned streams, including when reading only part of a log.
Closing a ZIP entry stream also closes its archive. Filtering, sentinel handling,
rotating-log selection and timestamp interpretation remain in the API/parser
callers; the shared module does not apply these policies.
