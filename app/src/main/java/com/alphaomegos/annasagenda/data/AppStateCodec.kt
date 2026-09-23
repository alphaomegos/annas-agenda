@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.alphaomegos.annasagenda

/**
 * Outcome of decoding a persisted app-state payload.
 *
 * The distinction between "there is no payload" and "there is a payload we
 * cannot read" is the whole point of this type. Collapsing the two into a
 * single `AppState?` is what made unreadable data indistinguishable from a
 * first launch: the app would fall back to an empty state and autosave would
 * then overwrite the still-perfectly-recoverable payload 400 ms later.
 */
sealed interface AppStateDecodeResult {

    data class Success(val state: AppState) : AppStateDecodeResult

    data class Failure(val cause: Throwable) : AppStateDecodeResult
}

/**
 * Decodes a stored payload, migrating older schema versions on the way.
 *
 * Pure: no Android dependencies, no I/O. That is deliberate — it keeps the
 * decision "is this payload readable?" unit-testable on a plain JVM instead of
 * requiring an instrumented test.
 */
internal fun decodeAppStateJsonOrFailure(raw: String): AppStateDecodeResult {
    if (raw.isBlank()) {
        return AppStateDecodeResult.Failure(
            IllegalArgumentException("Stored app state payload is blank")
        )
    }

    return runCatching {
        val migrated = migrateAppStateRawJson(raw)
        appStateStoreJson
            .decodeFromJsonElement(AppStateDto.serializer(), migrated)
            .toDomain()
    }.fold(
        onSuccess = { AppStateDecodeResult.Success(it) },
        onFailure = { AppStateDecodeResult.Failure(it) },
    )
}
