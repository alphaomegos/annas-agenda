package com.alphaomegos.annasagenda

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alphaomegos.annasagenda.dialogs.AnthropometryDayInputDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The rotation itself, from a terminal.
 *
 * Every rotation bug this project has had — 0043, 0044, 0055, 0089, 0090 —
 * was found by turning a phone, and every fix was believed rather than
 * checked, because checking meant an emulator. This is the first test to do
 * the checking, and it is one test on purpose: what it proves is not really
 * that this dialog keeps a number, but that Compose's state-restoration
 * machinery works here at all. If it does, the other four can follow.
 *
 * [StateRestorationTester] does what turning the phone does and nothing else:
 * the state is saved, the composition is thrown away and built again from
 * what was saved. That is the exact step where a plain `remember` loses its
 * contents and a `rememberSaveable` does not.
 *
 * One field is enabled, so there is exactly one place to type and no need to
 * name it.
 */
@RunWith(AndroidJUnit4::class)
class RotationKeepsWhatWasTypedTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theMeasurementDialogKeepsANumberThatWasHalfTyped() {
        val rotation = StateRestorationTester(compose)

        rotation.setContent {
            AnthropometryDayInputDialog(
                date = LocalDate.of(2026, 3, 2),
                initialEntry = null,
                enabledFieldIds = setOf(AnthropometryFieldIds.WEIGHT),
                onDismiss = {},
                onSave = {},
            )
        }

        // "72." is not a number yet, which is the whole point: it is what the
        // field holds between two keystrokes, and it is what used to vanish.
        compose.onNode(hasSetTextAction()).performTextInput("72.")
        compose.onNodeWithText("72.").assertExists()

        rotation.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("72.").assertExists()
    }
}
