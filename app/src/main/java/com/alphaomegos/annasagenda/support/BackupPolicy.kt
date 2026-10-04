package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/**
 * When the automatic backup is allowed to run.
 *
 * Both guards were paid for. The automatic snapshot overwrites the file it
 * wrote last time, so writing at the wrong moment does not add a bad backup —
 * it destroys the good one.
 *
 * - Before the state is loaded the in-memory state is empty. Backing that up
 *   replaces a real archive with an empty one.
 * - After a storage failure the in-memory state is also empty, deliberately, so
 *   the user can see the failure screen instead of a crash. That emptiness must
 *   not reach the archive either — the archive is exactly what the user would
 *   restore from.
 */
fun shouldWriteAutoBackup(
    isLoaded: Boolean,
    hasStorageFailure: Boolean,
): Boolean = isLoaded && !hasStorageFailure
