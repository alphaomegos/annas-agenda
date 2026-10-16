package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.data.MusicLocalFileDto
import com.alphaomegos.annasagenda.data.MusicLocalFilesDto
import com.alphaomegos.annasagenda.data.sidecarJson
import com.alphaomegos.annasagenda.model.MusicLocalFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which tracks play from a file of the phone's instead (16.10), kept in
 * [file] — the no-backup folder: a content:// address means nothing on
 * another phone. By track id; kept across signing out, as a choice about
 * this phone's own files.
 */
internal class MusicLocalFiles(private val file: File) {
    private val _files = MutableStateFlow(read())
    val files: StateFlow<Map<String, MusicLocalFile>> = _files.asStateFlow()

    private fun read(): Map<String, MusicLocalFile> = runCatching {
        if (!file.isFile) {
            emptyMap()
        } else {
            sidecarJson.decodeFromString(MusicLocalFilesDto.serializer(), file.readText()).files
                .filter { it.trackId.isNotBlank() && it.uri.isNotBlank() }
                .associate { it.trackId to MusicLocalFile(it.uri, it.name) }
        }
    }.getOrDefault(emptyMap())

    @Synchronized
    fun set(trackId: String, local: MusicLocalFile) {
        _files.value = _files.value + (trackId to local)
        save()
    }

    @Synchronized
    fun remove(trackId: String) {
        if (trackId !in _files.value) return
        _files.value = _files.value - trackId
        save()
    }

    private fun save() {
        runCatching {
            val dir = file.absoluteFile.parentFile ?: return
            dir.mkdirs()
            val next = File(dir, file.name + ".next")
            val dto = MusicLocalFilesDto(_files.value.map { (id, f) -> MusicLocalFileDto(id, f.uri, f.name) }.sortedBy { it.trackId })
            next.writeText(sidecarJson.encodeToString(MusicLocalFilesDto.serializer(), dto))
            if (!next.renameTo(file)) {
                file.delete()
                next.renameTo(file)
            }
        }
    }
}
