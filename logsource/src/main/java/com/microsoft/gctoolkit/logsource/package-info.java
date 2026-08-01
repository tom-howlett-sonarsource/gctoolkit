// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * File system utilities shared by the GCToolKit API and parser modules.
 * <p>
 * The package covers the three pieces of behaviour that both modules need from a GC log source:
 * <dl>
 * <dt>{@link com.microsoft.gctoolkit.logsource.LogFileFormat}</dt>
 * <dd>Discovery of the on disk format of a source, from the magic bytes of the file.</dd>
 * <dt>{@link com.microsoft.gctoolkit.logsource.LogFileSources}</dt>
 * <dd>Discovery of the individual sources making up a log, and their size in bytes.</dd>
 * <dt>{@link com.microsoft.gctoolkit.logsource.LogFileStreams}</dt>
 * <dd>Opening plain text, ZIP and GZIP sources as a stream of lines.</dd>
 * <dt>{@link com.microsoft.gctoolkit.logsource.LogFileTail}</dt>
 * <dd>Reading the last lines of a source.</dd>
 * </dl>
 */
package com.microsoft.gctoolkit.logsource;
