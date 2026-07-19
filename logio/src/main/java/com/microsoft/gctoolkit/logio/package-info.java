// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
/**
 * Shared production utilities for detecting a GC log's source format
 * (plain text, ZIP, GZIP, directory) and opening the corresponding line
 * streams. Used by both the API and parser modules to avoid duplicating
 * IO code.
 */
package com.microsoft.gctoolkit.logio;
