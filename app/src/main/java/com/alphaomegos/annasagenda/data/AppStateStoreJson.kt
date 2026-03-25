package com.alphaomegos.annasagenda

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

internal const val CURRENT_SCHEMA_VERSION = 3

@OptIn(ExperimentalSerializationApi::class)
internal val appStateStoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}