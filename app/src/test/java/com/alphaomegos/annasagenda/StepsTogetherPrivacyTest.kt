package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/** Steps together keeps its file where no backup reaches (0305): under noBackupFilesDir. */
class StepsTogetherPrivacyTest {
    @Test
    fun theFileIsWhereNoBackupGoes() {
        val source = mainSourceFiles().single { it.name == "StepsTogether.kt" }.readText()
        assertTrue(source.contains("File(File(context.applicationContext.noBackupFilesDir, \"sidecar\"), \"steps-together.json\")"))
    }
}
