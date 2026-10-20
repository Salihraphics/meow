package com.salihraphics.meow.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.domain.model.AnalysisInfo
import com.salihraphics.meow.domain.model.AnalysisStatus
import com.salihraphics.meow.domain.model.AnswerConfidence
import com.salihraphics.meow.domain.model.AnswerOrigin
import com.salihraphics.meow.domain.model.AnswerType
import com.salihraphics.meow.domain.model.KeyAnswer
import com.salihraphics.meow.domain.model.KeyStatus
import com.salihraphics.meow.domain.model.KeyTest
import com.salihraphics.meow.domain.model.KeyTestKind
import com.salihraphics.meow.domain.model.KeyTestStatus
import com.salihraphics.meow.feature.answerkey.AnswerKeyActions
import com.salihraphics.meow.feature.answerkey.AnswerKeyContent
import com.salihraphics.meow.feature.answerkey.AnswerKeyUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The answer-key review screen in its main states, for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AnswerKeyScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun answer(test: String, q: Int, v: String, low: Boolean = false, conflict: List<String> = emptyList(), manual: Boolean = false) =
        KeyAnswer(
            "$test-$q", test, q, if (v.any(Char::isDigit)) AnswerType.NUMBER else AnswerType.CHOICE, v, null,
            if (manual) AnswerOrigin.MANUAL else AnswerOrigin.DETECTED,
            if (low || conflict.isNotEmpty()) AnswerConfidence.LOW else AnswerConfidence.HIGH,
            if (conflict.isNotEmpty()) "CONFLICT" else if (low) "OCR_GUESS" else null, conflict, 118,
        )

    private val letters = "ACBDEDCABEBDCAEDBCAE"
    private val detected = listOf(
        KeyTest(
            "t1", "d", "d", "Test 1", 1, KeyTestKind.KEY_SECTION, KeyTestStatus.DETECTED, listOf(118), 2..9,
            letters.mapIndexed { i, c ->
                when (i) {
                    6 -> answer("t1", 7, "B", conflict = listOf("B", "D"))
                    11 -> answer("t1", 12, "D", low = true)
                    else -> answer("t1", i + 1, c.toString())
                }
            }.filter { it.question != 15 },
        ),
        KeyTest(
            "t2", "d", "d", "Test 2", 2, KeyTestKind.PAGE_MARGIN, KeyTestStatus.DETECTED, listOf(10, 11), null,
            letters.take(12).mapIndexed { i, c -> answer("t2", i + 1, c.toString(), manual = i == 3) },
        ),
    )

    private fun shoot(name: String, state: AnswerKeyUiState, dark: Boolean = false) {
        rule.setContent {
            MeowTheme(darkTheme = dark, mascotAnimationsEnabled = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AnswerKeyContent(state, AnswerKeyActions()) }
            }
        }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/answerkey/$name.png")
    }

    private val analysis = AnalysisInfo("d", AnalysisStatus.DONE, true, 120, 120, 0, 1L, null)
    private val found = AnswerKeyUiState("TYT Matematik Soru Bankası", 120, KeyStatus.Found(2, false), analysis, detected)

    @Test @Config(qualifiers = "w393dp-h852dp-xhdpi") fun reviewPhone() = shoot("phone_review", found)
    @Test @Config(qualifiers = "w393dp-h852dp-xhdpi") fun reviewPhoneDark() = shoot("phone_review_dark", found, dark = true)
    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi") fun reviewTablet() = shoot("tablet_review", found)
    @Test @Config(qualifiers = "w393dp-h852dp-xhdpi") fun notFound() =
        shoot("phone_not_found", AnswerKeyUiState("Paragraf 50 Günde", 64, KeyStatus.NotFound, analysis.copy(keyFound = false, textPages = 0, ocrReads = 12), emptyList()))
    @Test @Config(qualifiers = "w393dp-h852dp-xhdpi") fun searching() =
        shoot("phone_searching", AnswerKeyUiState("AYT Fizik Deneme 3", 48, KeyStatus.Searching(0.4f), null, emptyList()))
}
