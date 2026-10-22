package com.salihraphics.meow.feature.viewer

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWindow
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool
import com.salihraphics.meow.feature.annotation.AnnotationSession
import com.salihraphics.meow.feature.annotation.AnnotationToolState
import com.salihraphics.meow.feature.annotation.BlankPages
import com.salihraphics.meow.feature.study.FakeStudyDao
import com.salihraphics.meow.feature.study.StudyTimer
import com.salihraphics.meow.feature.study.StudyTimerController
import com.salihraphics.meow.feature.study.TimerStatus
import com.salihraphics.meow.testing.FakeAnnotationRepository
import com.salihraphics.meow.testing.sampleDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The study workspace driven by touch: tools in the panel or rail (never over the pages),
 * drawing on turned and zoomed pages, the highlighter, notes and the timer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1400dp-h1400dp-mdpi")
class StudyWorkspaceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val tablet = 1280.dp to 800.dp
    private val phone = 393.dp to 852.dp

    private val repo = FakeAnnotationRepository()
    private val marks = mutableListOf<Stroke>()
    private val session = AnnotationSession("doc-1", repo, CoroutineScope(Dispatchers.Unconfined), onStrokeAdded = { marks += it })
    private val ready = ViewerUiState.Ready(sampleDocument(pages = 10), BlankPages(10), initialPage = 0, annotations = session)
    private var clock = 0L
    private val studyDao = FakeStudyDao()
    private val timer = StudyTimerController(studyDao, CoroutineScope(Dispatchers.Unconfined), { clock }, { 1_700_000_000_000L + clock })
    private var rotation by mutableIntStateOf(0)

    private fun start(screen: Pair<Dp, Dp>) {
        rule.setContent { Viewer(screen) }
        rule.waitForIdle()
    }

    @Composable
    private fun Viewer(screen: Pair<Dp, Dp>) {
        MeowTheme(darkTheme = false, mascotAnimationsEnabled = false) {
            CompositionLocalProvider(LocalMeowWindow provides MeowWindow.fromDp(screen.first.value.toInt(), screen.second.value.toInt())) {
                Box(Modifier.requiredSize(screen.first, screen.second)) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        PdfViewerContent(
                            ready, { ViewportSnapshot(0) }, {}, {}, {},
                            workspace = ViewerWorkspace(rotation, { rotation = it }, timer),
                        )
                    }
                }
            }
        }
    }

    private fun page(number: Int) = rule.onNode(hasContentDescription("Sayfa $number / 10"))

    /** Taps a control by its label (content description, else text), scrolling the panel to it first. */
    private fun tap(label: String) {
        val byDescription = rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        val node = rule.onNode(if (byDescription) hasContentDescription(label) else hasText(label))
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    /**
     * Swipes between two points given as fractions of the whole shown page (on a tablet a page
     * is taller than the screen; its top is on screen in these tests, so offsets line up).
     */
    private fun drawOn(node: SemanticsNodeInteraction, from: Offset, to: Offset) {
        val size = node.fetchSemanticsNode().size
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        node.performTouchInput { swipe(Offset(w * from.x, h * from.y), Offset(w * to.x, h * to.y), 400) }
        rule.waitForIdle()
    }

    private fun pixelOnPage(node: SemanticsNodeInteraction, fx: Float, fy: Float): Int {
        val n = node.fetchSemanticsNode()
        return screenshot().getPixel((n.positionInRoot.x + n.size.width * fx).toInt(), (n.positionInRoot.y + n.size.height * fy).toInt())
    }

    private fun screenshot(): Bitmap {
        rule.waitForIdle()
        val root = rule.activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
        var bitmap: Bitmap? = null
        rule.runOnUiThread {
            bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(android.graphics.Canvas(it)) }
        }
        return bitmap!!
    }

    private fun isRed(c: Int) = android.graphics.Color.red(c) > 170 && android.graphics.Color.green(c) < 120 && android.graphics.Color.blue(c) < 120
    private fun isWhite(c: Int) = android.graphics.Color.red(c) > 240 && android.graphics.Color.green(c) > 240 && android.graphics.Color.blue(c) > 240

    private fun xs(s: Stroke) = s.points.filterIndexed { i, _ -> i % 2 == 0 }
    private fun ys(s: Stroke) = s.points.filterIndexed { i, _ -> i % 2 == 1 }

    /** Unclipped position: controls scrolled out of the panel's view still have one. */
    private fun position(label: String): Offset {
        val byDescription = rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        return rule.onNode(if (byDescription) hasContentDescription(label) else hasText(label)).fetchSemanticsNode().positionInRoot
    }

    private fun bounds(label: String): Rect {
        val byDescription = rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        return rule.onNode(if (byDescription) hasContentDescription(label) else hasText(label)).fetchSemanticsNode().boundsInRoot
    }

    // ---- Layout: nothing over the pages ----------------------------------------------------------

    private val tools = listOf("Gezin", "Kalem", "Fosforlu kalem", "Silgi", "Geri al", "Yinele")

    @Test
    fun onATabletEveryToolIsInThePanelBesideThePages() {
        start(tablet)
        val pages = rule.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        for (label in tools + listOf("Not ekle", "Başlat", "Yakınlaştır", "Uzaklaştır", "Sola döndür", "Sağa döndür")) {
            val b = position(label)
            assertTrue("$label should be right of the pages, not over them: $b vs $pages", b.x >= pages.right)
        }
        // And no tool is drawn over the bottom of the pages any more.
        assertTrue(rule.onAllNodesWithContentDescription("Çizim araçları").fetchSemanticsNodes().all { it.boundsInRoot.left >= pages.right })
    }

    @Test
    fun onAPhoneTheToolsAreARailBesideThePagesAndTheRestIsADrawer() {
        start(phone)
        val pages = rule.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        for (label in tools + "Not ekle") {
            assertTrue("$label beside the pages", bounds(label).left >= pages.right)
        }
        assertTrue(rule.onAllNodesWithContentDescription("Başlat").fetchSemanticsNodes().isEmpty())
        tap("Çalışma paneli")
        rule.mainClock.advanceTimeBy(600)
        rule.onNode(hasText("Başlat")).assertExistsCompat()
        rule.onNode(hasContentDescription("Sağa döndür")).assertExistsCompat()
        tap("Paneli kapat")
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithContentDescription("Sağa döndür").fetchSemanticsNodes().isEmpty())
    }

    private fun SemanticsNodeInteraction.assertExistsCompat() = fetchSemanticsNode()

    // ---- Rotation and zoom ----------------------------------------------------------------------

    @Test
    fun drawingOnATurnedPageStoresWhereItIsOnThePrintedPage() {
        start(tablet)
        tap("Sağa döndür")
        assertEquals(90, rotation)
        tap("Kalem")
        tap("Kırmızı")
        tap("Kalın çizgi")
        // Across the turned page, left to right at 40 % of its shown height…
        drawOn(page(1), Offset(0.3f, 0.4f), Offset(0.7f, 0.4f))
        val stroke = repo.all.values.single()
        // …is down the printed page, from 70 % to 30 % of its height, at 40 % of its width.
        assertTrue(xs(stroke).all { abs(it - 0.4f) < 0.01f })
        assertEquals(0.7f, ys(stroke).first(), 0.01f)
        assertEquals(0.3f, ys(stroke).last(), 0.01f)
        assertTrue(isRed(pixelOnPage(page(1), 0.5f, 0.4f)))
    }

    @Test
    fun inkStaysOnItsPrintedSpotWhenThePageIsTurnedZoomedAndTurnedBack() {
        start(tablet)
        tap("Kalem")
        tap("Kırmızı")
        tap("Kalın çizgi")
        drawOn(page(1), Offset(0.3f, 0.4f), Offset(0.7f, 0.4f))
        assertTrue(isRed(pixelOnPage(page(1), 0.5f, 0.4f)))

        tap("Sağa döndür")
        // Page (0.5, 0.4) is shown at (0.6, 0.5) once turned clockwise; the line now runs down.
        val shown = PageRotation.toShown(Offset(0.5f, 0.4f), 1f, 1f, 90)
        assertTrue(isRed(pixelOnPage(page(1), shown.x, shown.y)))
        assertTrue(isRed(pixelOnPage(page(1), 0.6f, 0.35f)))
        assertTrue(isWhite(pixelOnPage(page(1), 0.5f, 0.4f)))

        // Down to 50 %: the page shrinks, the line stays on the same spot of it.
        repeat(4) { tap("Uzaklaştır") }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertTrue(isRed(pixelOnPage(page(1), shown.x, shown.y)))

        tap("Orijinal yön")
        assertEquals(0, rotation)
        assertTrue(isRed(pixelOnPage(page(1), 0.5f, 0.4f)))
        assertEquals(1, repo.all.size) // turning never rewrote the stroke
    }

    @Test
    fun theSmallestZoomIsHalfSize() {
        start(tablet)
        val full = page(1).fetchSemanticsNode().size.width
        repeat(6) { tap("Uzaklaştır") }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val half = page(1).fetchSemanticsNode().size.width
        assertEquals(0.5f, half.toFloat() / full, 0.02f)
        // At the minimum, zooming out further is disabled.
        assertTrue(rule.onNode(hasContentDescription("Uzaklaştır")).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled))
    }

    // ---- Highlighter ------------------------------------------------------------------------------

    @Test
    fun theHighlighterIsATranslucentBandThatIsNeverTakenAsAnAnswer() {
        start(tablet)
        tap("Fosforlu kalem")
        tap("Yeşil fosforlu")
        tap("Geniş fosforlu")
        drawOn(page(1), Offset(0.2f, 0.5f), Offset(0.8f, 0.5f))
        val stroke = repo.all.values.single()
        assertEquals(StrokeTool.HIGHLIGHTER, stroke.tool)
        assertEquals(AnnotationToolState.HIGHLIGHT_COLORS[1], stroke.color)
        assertEquals(AnnotationToolState.HIGHLIGHT_WIDTHS[2], stroke.width)
        assertTrue("highlights are not answer marks", marks.isEmpty())
        // Light green over the white page: tinted, but far from opaque.
        val c = pixelOnPage(page(1), 0.5f, 0.5f)
        assertTrue(android.graphics.Color.green(c) > android.graphics.Color.red(c) + 20)
        assertTrue(android.graphics.Color.red(c) > 120)

        tap("Silgi")
        drawOn(page(1), Offset(0.5f, 0.4f), Offset(0.5f, 0.6f))
        assertTrue(stroke.id in repo.deleted)
        tap("Geri al")
        assertFalse(stroke.id in repo.deleted)
        tap("Yinele")
        assertTrue(stroke.id in repo.deleted)
    }

    // ---- Notes ------------------------------------------------------------------------------------

    @Test
    fun aNoteIsCreatedTypedMovedResizedAndDeleted() {
        start(tablet)
        tap("Not ekle")
        // Placed empty, no questions asked.
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        assertEquals("", repo.notes.values.single().text)
        tap("Yazı yaz")
        rule.onNode(hasSetTextAction()).performTextInput("Türev kuralları")
        rule.onNode(hasText("Kaydet")).performClick()
        rule.waitForIdle()
        val created = repo.notes.values.single()
        assertEquals("Türev kuralları", created.text)
        assertEquals(0, created.pageIndex)

        val pageWidth = page(1).fetchSemanticsNode().size.width.toFloat()
        val note = rule.onNode(hasContentDescription("Not: Türev kuralları"))
        note.performTouchInput { swipe(center, center + Offset(120f, 80f), 300) }
        rule.waitForIdle()
        val moved = repo.notes.values.single()
        assertEquals(created.x + 120f / pageWidth, moved.x, 0.02f)
        assertTrue(moved.y > created.y)

        rule.onNode(hasContentDescription("Notu boyutlandır")).performTouchInput { swipe(center, center + Offset(100f, 60f), 300) }
        rule.waitForIdle()
        val resized = repo.notes.values.single()
        assertEquals(moved.width + 100f / pageWidth, resized.width, 0.02f)
        assertEquals(moved.x, resized.x, 0.001f)

        // The × on the note itself (the panel's note list has one too).
        rule.onAllNodesWithContentDescription("Notu sil")[0].performClick()
        rule.waitForIdle()
        assertTrue(resized.id in repo.deletedNotes)
        tap("Geri al")
        assertFalse(resized.id in repo.deletedNotes)
    }

    @Test
    fun withThePenTheStudentWritesOnTheNoteItselfAndTheInkTravelsWithIt() {
        start(tablet)
        tap("Not ekle")
        val note = repo.notes.values.single()
        tap("Kalem")
        tap("Kırmızı")
        tap("Kalın çizgi")
        val box = rule.onNode(hasContentDescription("Not: Boş not"))
        box.performTouchInput { swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.5f), 300) }
        rule.waitForIdle()

        // The ink is the note's, in the note's own fractions; nothing went onto the page, and it
        // isn't taken as an answer mark.
        val ink = repo.all.values.single()
        assertEquals(note.id, ink.noteId)
        assertEquals(0.2f, xs(ink).first(), 0.02f)
        assertEquals(0.8f, xs(ink).last(), 0.02f)
        assertTrue(ys(ink).all { abs(it - 0.5f) < 0.02f })
        assertTrue(marks.isEmpty())
        assertTrue(rule.onAllNodes(hasContentDescription("Not: El yazılı not")).fetchSemanticsNodes().isNotEmpty())
        val shownBefore = rule.onNode(hasContentDescription("Not: El yazılı not")).fetchSemanticsNode().boundsInRoot
        val pixelBefore = screenshot().getPixel(shownBefore.center.x.toInt(), shownBefore.center.y.toInt())
        assertTrue(isRed(pixelBefore))

        // Moving the note with its handle (the pen is still out) takes the writing along.
        rule.onNode(hasContentDescription("Notu taşı")).performTouchInput { swipe(center, center + Offset(150f, 90f), 300) }
        rule.waitForIdle()
        val moved = repo.notes.values.single()
        assertTrue(moved.x > note.x && moved.y > note.y)
        assertEquals(ink, repo.all.values.single()) // stored ink unchanged: it is relative to the note
        val shownAfter = rule.onNode(hasContentDescription("Not: El yazılı not")).fetchSemanticsNode().boundsInRoot
        assertTrue(isRed(screenshot().getPixel(shownAfter.center.x.toInt(), shownAfter.center.y.toInt())))

        // Off the note, the pen still draws on the page.
        drawOn(page(1), Offset(0.1f, 0.6f), Offset(0.3f, 0.6f))
        assertEquals(listOf(null), repo.all.values.filter { it.id != ink.id }.map { it.noteId })

        // The eraser on the note removes its writing; undo brings it back.
        tap("Silgi")
        rule.onNode(hasContentDescription("Not: El yazılı not")).performTouchInput { swipe(Offset(width * 0.5f, height * 0.2f), Offset(width * 0.5f, height * 0.8f), 300) }
        rule.waitForIdle()
        assertTrue(ink.id in repo.deleted)
        tap("Geri al")
        assertFalse(ink.id in repo.deleted)
    }

    @Test
    fun aNoteCanHoldBothHandwritingAndTypedText() {
        start(tablet)
        tap("Not ekle")
        tap("Kalem")
        rule.onNode(hasContentDescription("Not: Boş not")).performTouchInput { swipe(Offset(width * 0.3f, height * 0.7f), Offset(width * 0.7f, height * 0.7f), 300) }
        rule.waitForIdle()
        tap("Yazı yaz")
        rule.onNode(hasSetTextAction()).performTextInput("Sınavdan önce")
        rule.onNode(hasText("Kaydet")).performClick()
        rule.waitForIdle()
        val note = repo.notes.values.single()
        assertEquals("Sınavdan önce", note.text)
        assertEquals(note.id, repo.all.values.single().noteId)
        rule.onNode(hasContentDescription("Not: Sınavdan önce")).fetchSemanticsNode()
    }

    @Test
    fun notesStayOnTheirPrintedSpotWhenThePageIsTurned() {
        start(tablet)
        tap("Not ekle")
        val note = repo.notes.values.single()
        val before = rule.onNode(hasContentDescription("Not: Boş not")).fetchSemanticsNode().boundsInRoot
        tap("Sağa döndür")
        val p = page(1).fetchSemanticsNode()
        val after = rule.onNode(hasContentDescription("Not: Boş not")).fetchSemanticsNode().boundsInRoot
        // The note's top-left page corner is shown at the top-right of its turned box.
        val corner = PageRotation.toShown(Offset(note.x, note.y), p.size.width.toFloat(), p.size.height.toFloat(), 90)
        assertEquals(p.positionInRoot.x + corner.x, after.right, 3f)
        assertEquals(p.positionInRoot.y + corner.y, after.top, 3f)
        assertTrue(before != after)
        assertEquals(note, repo.notes.values.single()) // stored position unchanged
    }

    // ---- Timer and cat ----------------------------------------------------------------------------

    @Test
    fun theTimerStartsPausesResumesAndResetsFromThePanel() {
        start(tablet)
        rule.onNode(hasText("25:00")).fetchSemanticsNode()
        tap("Başlat")
        rule.waitForIdle()
        assertEquals(TimerStatus.RUNNING, timer.timer.value.status)
        clock = 7 * StudyTimer.MINUTE_MS
        tap("Duraklat")
        assertEquals(TimerStatus.PAUSED, timer.timer.value.status)
        rule.onNode(hasText("18:00")).fetchSemanticsNode()
        tap("Devam et")
        assertEquals(TimerStatus.RUNNING, timer.timer.value.status)
        tap("Sıfırla")
        assertEquals(TimerStatus.IDLE, timer.timer.value.status)
        // Seven minutes, ended early: logged as studied time, not as a completed session…
        val session = studyDao.sessions.value.single()
        assertEquals(7 * StudyTimer.MINUTE_MS, session.elapsedMs)
        assertFalse(session.completed)
        // …and the cat says something kind about those 7 minutes.
        rule.onNode(hasText("7 dakika", substring = true)).fetchSemanticsNode()
    }

    @Test
    fun theDurationChangesInFiveMinuteSteps() {
        start(tablet)
        tap("Süreyi 5 dakika uzat")
        tap("Süreyi 5 dakika uzat")
        assertEquals(35 * StudyTimer.MINUTE_MS, timer.timer.value.durationMs)
        rule.onNode(hasText("35:00")).fetchSemanticsNode()
        tap("Süreyi 5 dakika kısalt")
        assertEquals(30 * StudyTimer.MINUTE_MS, timer.timer.value.durationMs)
    }

    @Test
    fun holdingPlusOrMinusChangesTheDurationFastAndStopsAtTheLimits() {
        start(tablet)
        val plus = rule.onNode(hasContentDescription("Süreyi 5 dakika uzat"))
        runCatching { plus.performScrollTo() }
        plus.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
        plus.performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(StudyTimer.MAX_DURATION_MS, timer.timer.value.durationMs)
        rule.onNode(hasText("Süre: 180 dk")).fetchSemanticsNode()

        val minus = rule.onNode(hasContentDescription("Süreyi 5 dakika kısalt"))
        minus.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
        minus.performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(StudyTimer.MIN_DURATION_MS, timer.timer.value.durationMs)

        // A tap is still exactly one step.
        tap("Süreyi 5 dakika uzat")
        assertEquals(StudyTimer.MIN_DURATION_MS + 5 * StudyTimer.MINUTE_MS, timer.timer.value.durationMs)
    }

    @Test
    fun theDurationCannotChangeDuringASession() {
        start(tablet)
        tap("Başlat")
        val plus = rule.onNode(hasContentDescription("Süreyi 5 dakika uzat"))
        plus.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(2_000)
        plus.performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(25 * StudyTimer.MINUTE_MS, timer.timer.value.durationMs)
    }

    @Test
    fun theStudyWorkspaceKeepsTheScreenOnAndHidesTheBars() {
        start(tablet)
        val window = rule.activity.window
        assertTrue(window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)!!
        assertFalse(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()))
        assertFalse(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars()))
        // A dialog from the workspace (here "Sayfaya git" via the page controls) stays immersive.
        tap("Sayfaya git")
        rule.onNode(hasText("Git")).fetchSemanticsNode()
        assertFalse(androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)!!.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()))
    }
}
