package com.alphaomegos.annasagenda

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.data.*

/**
 * The metro schemes that ship (assets/metro/) read without losing anything.
 *
 * The phone reads a library file forgivingly: a hint pointing at nothing is
 * dropped, a broken file is left out. This is where such a file is caught
 * instead, before it ships — every file decodes, and decoding drops nothing.
 * With no files there is nothing to check, which is a build without a library.
 */
class MetroLibraryFileTest {

    private fun libraryFiles(): List<File> {
        val dir = listOf("src/main/assets/$METRO_LIBRARY_DIR", "app/src/main/assets/$METRO_LIBRARY_DIR")
            .map(::File).firstOrNull { it.isDirectory } ?: return emptyList()
        return dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
    }

    @Test
    fun everyFileIsASchemeNamedForItsKey() {
        libraryFiles().forEach { file ->
            assertTrue("${file.name}: library files are <key>.json", file.name.matches(Regex("[a-z0-9_]+\\.json")))
            val key = file.name.removeSuffix(".json")
            val scheme = decodeMetroLibraryScheme(file.readText(), key)
            assertNotNull("${file.name} does not decode", scheme)
            assertEquals(key, scheme!!.librarySource)
            assertTrue("${file.name}: no city", scheme.city.isNotBlank())
            assertTrue("${file.name}: no lines", scheme.lines.isNotEmpty())
        }
    }

    @Test
    fun readingAFileDropsNothing() {
        libraryFiles().forEach { file ->
            val dto = appStateStoreJson.decodeFromString<MetroSchemeDto>(file.readText())
            val scheme = dto.toDomain()
            assertEquals("${file.name}: stations dropped", dto.stations.size, scheme.stations.size)
            assertEquals("${file.name}: transfers dropped", dto.transfers.size, scheme.transfers.size)
            assertEquals("${file.name}: exits dropped", dto.exits.size, scheme.exits.size)
            assertEquals("${file.name}: hints dropped", dto.hints.size, scheme.hints.size)
            assertEquals("${file.name}: ride times dropped", dto.segmentTimes.size, scheme.segmentTimes.size)
            assertEquals(
                "${file.name}: a station on no track, or on two",
                scheme.stations.map { it.id }.sorted(),
                scheme.lines.flatMap { l -> l.trunk + l.branches.flatMap { it.stationIds } }.sorted(),
            )
        }
    }

    @Test
    fun aSchemeTakenOutOfABackupReadsBackTheSame() {
        val scheme = MetroScheme(
            id = 1, city = "Москва",
            lines = listOf(MetroLine(id = 1, label = "1", name = "Сокольническая", color = 0xFFE42313, trunk = listOf(2, 3))),
            stations = listOf(MetroStation(2, 1, "Лубянка"), MetroStation(3, 1, "Чистые пруды")),
            librarySource = "this is not written",
        )
        val back = decodeMetroLibraryScheme(encodeMetroLibraryScheme(scheme), "moscow")
        assertEquals(scheme.copy(librarySource = "moscow"), back)
    }
}
