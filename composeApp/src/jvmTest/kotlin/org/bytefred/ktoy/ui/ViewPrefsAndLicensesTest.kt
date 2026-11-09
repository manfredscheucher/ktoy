package org.bytefred.ktoy.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.bytefred.ktoy.GeneratedLicenses

/** View settings remembered across starts; About -> License texts. */
@OptIn(ExperimentalTestApi::class)
class ViewPrefsAndLicensesTest {
    private val asmSrc = "        load  x\n        stop\nx:      42\n"

    // the tests share one in-memory store (-Dktoy.prefs=memory): each starts empty
    @BeforeTest
    @AfterTest
    fun emptyStore() = clearInMemoryPrefs()

    private fun ComposeUiTest.show(vm: SimViewModel) {
        setContent { KtoyTheme { ProvideMonoStyle { Surface { SimulatorScreen(vm, onLoad = {}) } } } }
        if (onAllNodes(hasText("Welcome to ktoy")).fetchSemanticsNodes().isNotEmpty()) onNodeWithText("Start").performClick()
        waitForIdle()
    }

    // a menu title ignores a click within 500 ms of its menu closing by an outside click (MenuButton)
    // (View check items keep their menu open: then the item is clicked right away)
    private fun ComposeUiTest.pick(menu: String, item: String) {
        if (onAllNodes(hasText(item, substring = true)).fetchSemanticsNodes().isEmpty()) {
            Thread.sleep(600)
            onNodeWithTag("menu-$menu").performClick()
            waitForIdle()
        }
        onNodeWithText(item, substring = true).performClick()
        waitForIdle()
    }

    @Test
    fun withNothingStoredTheDefaultsHold() {
        assertEquals(null, loadPref(LINE_NUMBERS_PREF))
        val vm = SimViewModel()
        assertTrue(vm.showLineNumbers)
        assertTrue(vm.autoScroll)
        assertEquals(LayoutMode.AUTO, vm.layoutMode)
    }

    @Test
    fun theViewMenuSettingsComeBackOnTheNextStart() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("p.toys", asmSrc) })
        pick("View", "Line numbers")
        pick("View", "Auto-scroll to PC")
        pick("View", "portrait")
        val next = SimViewModel()
        assertFalse(next.showLineNumbers)
        assertFalse(next.autoScroll)
        assertEquals(LayoutMode.PORTRAIT, next.layoutMode)
    }

    @Test
    fun theAutoBuildTogglesAreNotRemembered() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("p.toys", asmSrc) }
        show(vm)
        pick("Assembly", "Auto assemble on edit")
        assertEquals(SimViewModel.AutoBuild.ASSEMBLE, vm.autoBuild)
        assertEquals(null, SimViewModel().autoBuild)
    }

    @Test
    fun theLicenseTextsAreTheFilesInTheRepo() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "doc/licenses").isDirectory }
        val expected = listOf(
            "ktoy (MIT)" to File(root, "LICENSE"),
            "Toy CPU: toycpu by Jim Hall (MIT)" to File(root, "doc/licenses/LICENSE_TOYCPU.txt"),
            "Font: DejaVu Sans Mono (Bitstream Vera license)" to File(root, "doc/licenses/LICENSE_DEJAVU.txt"),
        )
        assertEquals(expected.map { it.first }, GeneratedLicenses.NOTICES.map { it.title })
        for ((notice, pair) in GeneratedLicenses.NOTICES.zip(expected)) {
            assertEquals(pair.second.readText().replace("\r", "").trimEnd(), notice.text, notice.title)
        }
    }

    @Test
    fun aboutShowsTheLicenseTextsOnRequest() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("p.toys", asmSrc) })
        pick("Help", "About ktoy")
        onNodeWithText("Toy CPU: toycpu by Jim Hall (MIT)").assertDoesNotExist()
        onNodeWithText("License texts").performScrollTo().performClick()
        waitForIdle()
        for (n in GeneratedLicenses.NOTICES) onNodeWithText(n.title).assertExists()
        onNodeWithText("Copyright (c) 2022 Jim Hall", substring = true).assertExists()
        onNodeWithText("Hide license texts").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Toy CPU: toycpu by Jim Hall (MIT)").assertDoesNotExist()
    }
}
