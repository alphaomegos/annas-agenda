package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.data.MusicPhonePrefsDto
import com.alphaomegos.annasagenda.data.sidecarJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** The phone's music choices: the folders the Local tab lists, and the tracks hidden from it. */
internal data class MusicPhoneChoices(val folders: Set<String> = emptySet(), val hidden: Set<String> = emptySet())

/**
 * The phone's music choices (02.11), kept in [file] in the no-backup folder: folders
 * and files are this phone's own, and mean nothing on another. Hiding a track never
 * touches its file; it is kept by its key (folder and name) and comes back from the
 * list of hidden ones in the music settings.
 */
internal class MusicPhonePrefs(private val file: File) {
    private val _choices = MutableStateFlow(read())
    val choices: StateFlow<MusicPhoneChoices> = _choices.asStateFlow()

    private fun read(): MusicPhoneChoices = runCatching {
        if (!file.isFile) {
            MusicPhoneChoices()
        } else {
            val dto = sidecarJson.decodeFromString(MusicPhonePrefsDto.serializer(), file.readText())
            MusicPhoneChoices(dto.folders.filter { it.isNotBlank() }.toSet(), dto.hidden.filter { it.isNotBlank() }.toSet())
        }
    }.getOrDefault(MusicPhoneChoices())

    @Synchronized
    fun setFolders(folders: Set<String>) {
        if (folders == _choices.value.folders) return
        _choices.value = _choices.value.copy(folders = folders)
        save()
    }

    @Synchronized
    fun hide(key: String) {
        if (key in _choices.value.hidden) return
        _choices.value = _choices.value.copy(hidden = _choices.value.hidden + key)
        save()
    }

    @Synchronized
    fun unhide(key: String) {
        if (key !in _choices.value.hidden) return
        _choices.value = _choices.value.copy(hidden = _choices.value.hidden - key)
        save()
    }

    private fun save() {
        runCatching {
            val dir = file.absoluteFile.parentFile ?: return
            dir.mkdirs()
            val next = File(dir, file.name + ".next")
            val c = _choices.value
            next.writeText(sidecarJson.encodeToString(MusicPhonePrefsDto.serializer(), MusicPhonePrefsDto(c.folders.sorted(), c.hidden.sorted())))
            if (!next.renameTo(file)) {
                file.delete()
                next.renameTo(file)
            }
        }
    }
}
