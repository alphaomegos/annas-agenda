package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.data.MusicPlayerMemoryDto
import com.alphaomegos.annasagenda.data.sidecarJson
import com.alphaomegos.annasagenda.data.toDomain
import com.alphaomegos.annasagenda.data.toDto
import com.alphaomegos.annasagenda.model.MusicPlayerMemory
import java.io.File

/**
 * Where the player was, kept in [file] (16.10) — the app's no-backup folder:
 * a queue of this phone's tracks means nothing on another. Written whole
 * beside itself and moved over; one that cannot be read is no memory.
 */
internal class MusicPlayerMemoryFile(private val file: File) {

    fun read(): MusicPlayerMemory? = runCatching {
        if (!file.isFile) null else sidecarJson.decodeFromString(MusicPlayerMemoryDto.serializer(), file.readText()).toDomain()
    }.getOrNull()

    fun write(memory: MusicPlayerMemory) {
        runCatching {
            val dir = file.absoluteFile.parentFile ?: return
            dir.mkdirs()
            val next = File(dir, file.name + ".next")
            next.writeText(sidecarJson.encodeToString(MusicPlayerMemoryDto.serializer(), memory.toDto()))
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
