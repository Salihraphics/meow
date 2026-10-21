package com.salihraphics.meow.feature.answerkey

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import com.salihraphics.meow.analysis.AnalysisOutcome
import com.salihraphics.meow.analysis.key.AnswerKeyParseResult
import com.salihraphics.meow.analysis.key.AnswerNote
import com.salihraphics.meow.analysis.key.Confidence
import com.salihraphics.meow.analysis.key.DetectedAnswer
import com.salihraphics.meow.analysis.key.DetectedTest
import com.salihraphics.meow.analysis.key.KeyClues
import com.salihraphics.meow.analysis.key.KeySourceKind
import com.salihraphics.meow.analysis.key.TestIssue
import com.salihraphics.meow.analysis.key.TestRef
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.data.answerkey.AnswerKeyRepository
import com.salihraphics.meow.data.library.db.MeowDatabase
import com.salihraphics.meow.domain.model.AnswerOrigin
import com.salihraphics.meow.domain.model.AnswerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Review, correct and confirm through the real screen, ViewModel and database. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class AnswerKeyReviewTest {
    @get:Rule val rule = createComposeRule()

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MeowDatabase::class.java).allowMainThreadQueries().build()
    private val keys = AnswerKeyRepository(db)

    @After fun tearDown() = db.close()

    private fun waitForText(text: String) = rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun show(): AnswerKeyViewModel {
        val vm = AnswerKeyViewModel("doc", flowOf("TYT Deneme" to 40), keys, MutableStateFlow(emptyMap()), searchAgainAction = {})
        rule.setContent { MeowTheme(darkTheme = false, mascotAnimationsEnabled = false) { AnswerKeyRoute(vm, onBack = {}) } }
        return vm
    }

    @Test
    fun aConflictMustBeResolvedBeforeConfirming() {
        runBlocking {
            keys.saveOutcome(
                "doc", "h",
                AnalysisOutcome(
                    AnswerKeyParseResult(
                        listOf(
                            DetectedTest(
                                TestRef(1, "Test 1"), KeySourceKind.KEY_SECTION, listOf(39), listOf(0, 9),
                                listOf(
                                    DetectedAnswer(1, "A", Confidence.HIGH, 39),
                                    DetectedAnswer(2, "B", Confidence.LOW, 39, AnswerNote.CONFLICT, listOf("B", "D")),
                                    DetectedAnswer(3, "C", Confidence.HIGH, 39),
                                ),
                                listOf(TestIssue.Conflict(2, listOf("B", "D"))),
                            ),
                        ),
                    ),
                    40, 40, 0,
                ),
            )
        }
        show()
        waitForText("Test 1")
        rule.onNodeWithText("Sorular: s. 1–10").assertExists()
        rule.onNodeWithText("1 soruda iki farklı cevap var", substring = true).assertExists()

        rule.onNodeWithText("Onayla").performClick()
        waitForText("Onaylamadan önce 1 çakışmayı çöz")
        assertFalse(runBlocking { keys.tests("doc") }.single().isConfirmed)

        // Open question 2 (marked uncertain), pick D, save.
        rule.onNode(hasContentDescription("Soru 2: B, Emin değilim")).performClick()
        rule.onNodeWithText("Kitapta iki farklı cevap var: B / D").assertExists()
        rule.onNode(hasContentDescription("D")).performClick() // one tap saves a correction
        rule.waitUntil(5_000) { runBlocking { keys.tests("doc") }.single().answers[1].value == "D" }
        assertEquals(AnswerOrigin.MANUAL, runBlocking { keys.tests("doc") }.single().answers[1].origin)

        rule.onNodeWithText("Onayla").performClick()
        rule.waitUntil(5_000) { runBlocking { keys.tests("doc") }.single().isConfirmed }
        waitForText("Cevap anahtarı onaylandı.")
    }

    @Test
    fun noKeyFoundSaysSoAndManualEntryWorks() {
        runBlocking { keys.saveOutcome("doc", "h", AnalysisOutcome(AnswerKeyParseResult(emptyList()), 10, 10, 0)) }
        val vm = show()
        waitForText("güvenilir bir cevap anahtarı bulamadım")
        rule.onNodeWithText("Test ekle").assertExists()

        // What the add-test and add-answer dialogs call. (Typing into dialog text fields hangs
        // Robolectric's idling, so the dialogs' text entry is exercised on devices only.)
        vm.addTest("Deneme 2", 2)
        waitForText("Deneme 2")
        val testId = runBlocking { keys.tests("doc") }.single().id
        vm.setAnswer(testId, 1, AnswerType.NUMBER, "12,5", 0.1)
        vm.setAnswer(testId, 2, AnswerType.CHOICE, "C", null)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("12,5").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasContentDescription("Soru 2: C, Elle girildi")).assertExists()
        assertEquals(2, rule.onAllNodesWithText("Elle girildi").fetchSemanticsNodes().size) // legend + the test's source line
        val answers = runBlocking { keys.tests("doc") }.single().answers
        assertEquals(listOf(AnswerType.NUMBER, AnswerType.CHOICE), answers.map { it.type })
        assertTrue(answers.all { it.origin == AnswerOrigin.MANUAL })
    }

    @Test
    fun whenNothingIsFoundItSaysWhyAndOffersPagesKeyPdfAndManualEntry() {
        val clues = KeyClues(headingPages = listOf(229, 230), answerLines = 0)
        runBlocking {
            keys.saveOutcome("doc", "h", AnalysisOutcome(AnswerKeyParseResult(emptyList(), clues), 240, 236, 12, ocrPages = listOf(239, 238, 229, 230)))
        }
        var pages: List<Int>? = null
        val vm = AnswerKeyViewModel("doc", flowOf("55 Günde TYT Matematik" to 240), keys, MutableStateFlow(emptyMap()), searchAgainAction = {},
            readKeyPagesAction = { pages = it })
        rule.setContent { MeowTheme(darkTheme = false, mascotAnimationsEnabled = false) { AnswerKeyRoute(vm, onBack = {}) } }
        waitForText("Neye baktım?")
        rule.onNodeWithText("s. 230, s. 231", substring = true).assertExists()
        rule.onNodeWithText("Cevap satırına benzeyen", substring = true).assertExists()
        rule.onNodeWithText("Ayrı anahtar PDF'i ekle").assertExists()
        rule.onNodeWithText("Elle gir").assertExists()
        rule.onNodeWithText("Anahtar hangi sayfalarda?").assertExists()
        // What the pages dialog's "Oku" sends for pages 230–231. (Dialogs with text fields never
        // go idle under Robolectric, so the dialog itself is exercised on devices only.)
        vm.readKeyPages(230, 231)
        assertEquals(listOf(229, 230), pages)
    }
}
