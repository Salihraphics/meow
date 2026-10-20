package com.salihraphics.meow.feature.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWindow
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.testing.sampleDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Rotation regression test. The whole viewer is torn down and rebuilt (as an activity recreation
 * does), in a different window shape and layout each time, with only the ViewModel's snapshot
 * carried over. Interaction happens through real gestures and buttons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1400dp-h1400dp-mdpi")
class ViewportRestorationTest {
    @get:Rule val rule = createComposeRule()

    private data class Screen(val name: String, val width: Dp, val height: Dp)

    private val phonePortrait = Screen("phone portrait", 393.dp, 852.dp)
    private val phoneLandscape = Screen("phone landscape", 852.dp, 393.dp) // two panes + side panel
    private val tabletLandscape = Screen("tablet landscape", 1280.dp, 800.dp)
    private val tabletPortrait = Screen("tablet portrait", 800.dp, 1280.dp)

    /** Plays the ViewModel's part: the only thing that survives a recreation. */
    private var saved: ViewportSnapshot = ViewportSnapshot(pageIndex = 0)
    private var screen by mutableStateOf(phonePortrait)
    private var generation by mutableStateOf(0)
    private val ready = ViewerUiState.Ready(sampleDocument(pages = 40), StubLoader(40), initialPage = 0)

    private fun start() {
        rule.setContent {
            key(generation) {
                val current = screen
                MeowTheme(darkTheme = false, mascotAnimationsEnabled = false) {
                    CompositionLocalProvider(
                        LocalMeowWindow provides MeowWindow.fromDp(current.width.value.toInt(), current.height.value.toInt()),
                    ) {
                        Box(Modifier.requiredSize(current.width, current.height)) {
                            Surface(color = MaterialTheme.colorScheme.background) {
                                PdfViewerContent(
                                    state = ready,
                                    initialViewport = { saved },
                                    onViewportChanged = { saved = it },
                                    onPageChanged = {},
                                    onBack = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /** Destroys the viewer and rebuilds it on [next], like rotating the device. */
    private fun rotateTo(next: Screen) {
        screen = next
        generation++
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
    }

    private fun assertSamePosition(expected: ViewportSnapshot, where: String) {
        val actual = saved
        assertEquals("page after $where", expected.pageIndex, actual.pageIndex)
        assertEquals("zoom after $where", expected.zoom, actual.zoom, 0.0001f)
        assertTrue(
            "offset within page after $where: expected ${expected.pageOffsetFraction}, was ${actual.pageOffsetFraction}",
            abs(expected.pageOffsetFraction - actual.pageOffsetFraction) < 0.02f,
        )
        assertTrue(
            "sideways pan after $where: expected ${expected.horizontalFraction}, was ${actual.horizontalFraction}",
            abs(expected.horizontalFraction - actual.horizontalFraction) < 0.02f,
        )
    }

    private fun readSomewhereZoomedIn() {
        val pages = rule.onNode(hasScrollToIndexAction())
        pages.performScrollToIndex(17)
        // Part-way into page 17, then zoom in twice with the button, then pan sideways.
        pages.performTouchInput { swipe(Offset(centerX, centerY + 150f), Offset(centerX, centerY - 150f), 400) }
        rule.waitForIdle()
        repeat(2) {
            rule.onNode(hasContentDescription("Yakınlaştır")).performClick()
            rule.waitForIdle()
        }
        pages.performTouchInput { swipe(Offset(centerX + 120f, centerY), Offset(centerX - 120f, centerY), 400) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
    }

    @Test
    fun positionZoomAndPanSurviveRotationAcrossLayouts() {
        start()
        readSomewhereZoomedIn()
        val reading = saved
        assertTrue("should be around page 17", reading.pageIndex in 17..18)
        assertEquals(1.5625f, reading.zoom, 0.0001f)
        assertTrue("should be part-way into the page", reading.pageOffsetFraction > 0.05f)
        assertTrue("should be panned sideways", reading.horizontalFraction > 0.05f)

        for (next in listOf(phoneLandscape, tabletLandscape, tabletPortrait, phonePortrait)) {
            rotateTo(next)
            assertSamePosition(reading, next.name)
            // The page shown to the student agrees with the restored position.
            rule.onNodeWithText("${reading.pageIndex + 1} / 40", substring = true, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun unzoomedPositionSurvivesRotation() {
        start()
        val pages = rule.onNode(hasScrollToIndexAction())
        pages.performScrollToIndex(5)
        pages.performTouchInput { swipe(Offset(centerX, centerY + 100f), Offset(centerX, centerY - 100f), 300) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val reading = saved
        assertEquals(1f, reading.zoom)
        assertEquals(0f, reading.horizontalFraction)

        rotateTo(phoneLandscape)
        assertSamePosition(reading, "phone landscape")
        rotateTo(phonePortrait)
        assertSamePosition(reading, "back to portrait")
    }

    @Test
    fun restoringWaitsForLayoutInsteadOfReportingAHalfRestoredPosition() {
        saved = ViewportSnapshot(pageIndex = 30, pageOffsetFraction = 0.5f, zoom = 2f, horizontalFraction = 0.75f)
        val expected = saved
        start()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertNotNull(saved)
        assertSamePosition(expected, "a cold start from a saved snapshot")
    }
}

private class StubLoader(override val pageCount: Int) : PdfPageLoader {
    override fun aspectRatio(index: Int) = 595f / 842f
    override suspend fun load(index: Int, widthPx: Int): ImageBitmap =
        android.graphics.Bitmap.createBitmap(64, 90, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}
