package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.data.MusicSavePlanDto
import com.alphaomegos.annasagenda.data.sidecarJson
import com.alphaomegos.annasagenda.data.toDomain
import com.alphaomegos.annasagenda.data.toDto
import com.alphaomegos.annasagenda.model.MusicSaveItem
import java.io.File

/**
 * What "save all" is to fetch (16.10), written down for its worker in [file]
 * — the no-backup folder: a list of hundreds of tracks is more than a
 * worker's own input may hold. Read back whole or not at all.
 */
internal class MusicSavePlanFile(private val file: File) {

    fun read(): List<MusicSaveItem>? = runCatching {
        if (!file.isFile) null else sidecarJson.decodeFromString(MusicSavePlanDto.serializer(), file.readText()).items.map { it.toDomain() }
    }.getOrNull()

    fun write(items: List<MusicSaveItem>) {
        runCatching {
            val dir = file.absoluteFile.parentFile ?: return
            dir.mkdirs()
            val next = File(dir, file.name + ".next")
            next.writeText(sidecarJson.encodeToString(MusicSavePlanDto.serializer(), MusicSavePlanDto(items.map { it.toDto() })))
            if (!next.renameTo(file)) {
                file.delete()
                next.renameTo(file)
            }
        }
    }

    fun forget() {
        runCatching { file.delete() }
    }
}
