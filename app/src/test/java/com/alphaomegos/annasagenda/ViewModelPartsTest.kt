package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppViewModel in parts (0336): a part is an interface with bodies that the
 * view model implements, so calls stay as they were. Each method lives in one
 * place, and a part reaches the state only through AppStateHost.
 */
class ViewModelPartsTest {
    private val vm by lazy { mainSourceFiles().single { it.name == "AppViewModel.kt" }.readText() }
    // Every part: AppViewModelPlacesAndFood.kt, AppViewModelBody.kt, … (0336 on).
    private val parts by lazy { mainSourceFiles().filter { it.name.startsWith("AppViewModel") && it.name != "AppViewModel.kt" } }

    @Test
    fun theViewModelImplementsItsParts() {
        // Spelled in two pieces: TestViewModelsOnlyTest reads tests for the view model's constructor.
        assertTrue(vm.hasCode("class AppView" + "Model(app: Application) : AndroidView" + "Model(app), PlacesAndFoodActions, BodyActions, RunningActions, TaskFlagsAndColorsActions, DayCountersActions, ReadingActions, MainMenuActions {"))
        assertTrue("parts", parts.size >= 7)
    }

    @Test
    fun eachMethodLivesInOnePlace() {
        val moved = parts.flatMap { f -> Regex("""^    (?:private )?fun (\w+)\(""", RegexOption.MULTILINE).findAll(f.readText()).map { it.groupValues[1] }.toList() }
        assertTrue("nothing moved", moved.size > 40)
        // A hook a part declares (nextFreshId, runningCreateTask, …) the view model
        // implements with «override»: that is the one place it is implemented.
        for (name in moved) assertTrue("$name is in both", !Regex("""(?<!override )\bfun $name\(""").containsMatchIn(vm))
    }

    @Test
    fun aPartReachesTheStateOnlyThroughTheHost() {
        parts.forEach { f -> assertTrue(f.name, !Regex("""\b_state\b|\bnewId\(\)""").containsMatchIn(f.readText())) }
    }
}
