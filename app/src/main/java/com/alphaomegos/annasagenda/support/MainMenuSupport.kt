package com.alphaomegos.annasagenda

fun normalizeMainMenuOrderIds(ids: Iterable<String>): List<String> =
    ids.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

fun normalizeMainMenuItemId(id: String): String? =
    id.trim().takeIf { it.isNotEmpty() }