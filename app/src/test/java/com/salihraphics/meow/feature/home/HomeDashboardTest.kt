package com.salihraphics.meow.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.domain.model.ExamDate
import com.salihraphics.meow.domain.model.ExamGoal
import com.salihraphics.meow.domain.model.ExamGoalType
import com.salihraphics.meow.domain.model.ExamSession
import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.screenshots.SampleHome
import com.salihraphics.meow.screenshots.sampleNow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class HomeDashboardTest {
    @get:Rule val rule = createComposeRule()

    private val opened = mutableListOf<String>()
    private val pages = mutableListOf<Pair<String, Int>>()
    private val saved = mutableListOf<ExamGoal?>()
    private var added = 0
    private var library = 0
    private val actions = HomeActions(
        onOpenLibrary = { library++ },
        onAddPdf = { added++ },
        onOpenDocument = { d: PdfDocument -> opened += d.id },
        onOpenPage = { id, page -> pages += id to page },
        onSaveExamGoal = { saved += it },
    )

    private fun show(full: Boolean = true, goal: ExamGoal? = ExamGoal(ExamGoalType.YKS), now: Long = sampleNow) = rule.setContent {
        MeowTheme(mascotAnimationsEnabled = false) { SampleHome(StudyProfile(displayName = "Deniz"), full, goal, now, actions) }
    }

    private fun tap(text: String) = rule.onNode(hasText(text)).performScrollTo().performClick()

    @Test
    fun anEmptyStartOffersImportAndSaysNothingIsRecordedYet() {
        show(full = false, goal = null)
        rule.onNodeWithText("İlk PDF'ini ekle").assertIsDisplayed()
        rule.onNode(hasText("Henüz kayıtlı çalışma yok", substring = true)).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("PDF ekle")[0].performClick()
        rule.onAllNodesWithText("PDF ekle")[1].performScrollTo().performClick()
        assertEquals(2, added)
        // No invented numbers anywhere.
        assertEquals(0, rule.onAllNodesWithText("Bu hafta", substring = false).fetchSemanticsNodes().size - 1)
    }

    @Test
    fun everyDashboardActionLeadsSomewhere() {
        show()
        rule.onAllNodesWithText("Sayfa 42 / 312")[0].assertIsDisplayed()
        tap("Devam et")
        assertEquals(listOf("1"), opened)
        tap("AYT Fizik Deneme 3")
        assertEquals(listOf("1", "2"), opened)
        tap("Türevde zincir kuralını tekrar et, 12. soru!")
        tap("El yazısı not")
        assertEquals(listOf("1" to 41, "2" to 11), pages)
        tap("Kütüphane · 4")
        assertEquals(1, library)
        tap("PDF ekle")
        assertEquals(1, added)
    }

    @Test
    fun yksCountsDownToTytAndListsAytWithTheEstimateLabelled() {
        show()
        rule.onNode(hasContentDescription("TYT için 239 gün 13 saat 6 dakika kaldı")).assertIsDisplayed()
        rule.onNodeWithText("YKS · TYT").assertIsDisplayed()
        rule.onNodeWithText("Tahmini tarih").assertIsDisplayed()
        rule.onNode(hasText("AYT / YDT: 20 Haziran 2027", substring = true)).assertIsDisplayed()
    }

    @Test
    fun theCountdownTicksEverySecondFromTheTargetTime() {
        rule.mainClock.autoAdvance = false
        val start = sampleNow
        rule.setContent {
            MeowTheme(mascotAnimationsEnabled = false) {
                HomeContent(
                    StudyProfile(), HomeDocuments(), emptyList(), StudySummary(), ExamGoal(ExamGoalType.YKS), { null }, HomeActions(),
                    clock = { start + rule.mainClock.currentTime },
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        // 13:06:46 left. The spoken description leaves out seconds, so watch the minute change.
        rule.onNode(hasContentDescription("TYT için 239 gün 13 saat 6 dakika kaldı")).assertExists()
        rule.mainClock.advanceTimeBy(46_000)
        rule.onNode(hasContentDescription("TYT için 239 gün 13 saat 6 dakika kaldı")).assertExists()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNode(hasContentDescription("TYT için 239 gün 13 saat 5 dakika kaldı")).assertExists()
    }

    @Test
    fun afterTytStartsTheFocusMovesToAytAndAfterBothTheGoalIsDone() {
        show(now = ExamSession.TYT.defaultDate.toMillis() + 60_000)
        rule.onNodeWithText("YKS · AYT / YDT").assertIsDisplayed()
        rule.onNodeWithText("TYT tamamlandı").assertIsDisplayed()
    }

    @Test
    fun passedExamsShowACompletedStateNotNegativeNumbers() {
        show(now = ExamSession.AYT_YDT.defaultDate.toMillis() + 1)
        rule.onNodeWithText("YKS tamamlandı. Emeğine sağlık!").assertIsDisplayed()
        tap("Yeni hedef belirle")
        rule.onNodeWithText("Sınav hedefi").assertIsDisplayed()
    }

    @Test
    fun onExamDayItSaysSo() {
        val lgs = ExamGoal(ExamGoalType.LGS, mapOf(ExamSession.LGS to ExamDate(2026, 10, 22, 23, 0)))
        show(goal = lgs)
        rule.onNodeWithText("Sınav bugün. Başarılar!").assertIsDisplayed()
        rule.onNodeWithText("Senin girdiğin tarih").assertIsDisplayed()
    }

    @Test
    fun theGoalIsSetUpFromHomeAndCanBeTurnedOff() {
        show(goal = null)
        tap("Hedef belirle")
        rule.onNodeWithText("YKS").performClick()
        rule.onNodeWithText("TYT · Tahmini tarih").assertIsDisplayed()
        rule.onNodeWithText("AYT / YDT · Tahmini tarih").assertIsDisplayed()
        rule.onNodeWithText("Kaydet").performClick()
        assertEquals(listOf<ExamGoal?>(ExamGoal(ExamGoalType.YKS)), saved)
    }

    @Test
    fun aHiddenCountdownCanBeTurnedBackOnAndNoGoalCanBeChosen() {
        show(goal = ExamGoal(ExamGoalType.LGS, enabled = false))
        rule.onNodeWithText("Geri sayım kapalı · LGS").assertIsDisplayed()
        tap("Aç")
        assertEquals(ExamGoal(ExamGoalType.LGS, enabled = true), saved.single())

        tap("Sınav sayacı")
        rule.onNodeWithText("Hedef yok").performClick()
        rule.onNodeWithText("Kaydet").performClick()
        assertEquals(null, saved.last())
    }
}
