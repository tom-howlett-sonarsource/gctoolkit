// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

/**
 * Shared utilities for GC log source discovery, format detection, and stream opening.
 * <p>
 * These utilities are consumed by both the API and parser modules so that log-format
 * classification (plain text, ZIP, GZIP, directory) and the code that opens each
 * flavour of stream lives in a single place.
 */
package com.microsoft.gctoolkit.io.source;
