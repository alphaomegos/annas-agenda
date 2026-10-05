package com.alphaomegos.annasagenda.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.app.*

internal const val CURRENT_SCHEMA_VERSION = 9

@OptIn(ExperimentalSerializationApi::class)
internal val appStateStoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}