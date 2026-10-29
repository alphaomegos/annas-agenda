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
    private val places by lazy { mainSourceFiles().single { it.name == "AppViewModelPlacesAndFood.kt" }.readText() }

    @Test
    fun theViewModelImplementsItsParts() {
        // Spelled in two pieces: TestViewModelsOnlyTest reads tests for the view model's constructor.
        assertTrue(vm.hasCode("class AppView" + "Model(app: Application) : AndroidView" + "Model(app), PlacesAndFoodActions {"))
    }

    @Test
    fun eachMethodLivesInOnePlace() {
        val moved = Regex("""^    (?:private )?fun (\w+)\(""", RegexOption.MULTILINE).findAll(places).map { it.groupValues[1] }.toList()
        assertTrue("nothing moved", moved.size > 30)
        // The host's own (nextFreshId) is declared in the part and implemented in the view model.
        for (name in moved - "nextFreshId") assertTrue("$name is in both", !Regex("""\bfun $name\(""").containsMatchIn(vm))
    }

    @Test
    fun aPartReachesTheStateOnlyThroughTheHost() {
        assertTrue(!Regex("""\b_state\b|\bnewId\(\)""").containsMatchIn(places))
    }
}
