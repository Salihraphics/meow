package com.salihraphics.meow.core.designsystem

import com.salihraphics.meow.core.designsystem.adaptive.MeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.WidthClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeowWindowTest {
    @Test
    fun phonePortraitIsSinglePane() {
        val w = MeowWindow.fromDp(393, 852)
        assertEquals(WidthClass.Compact, w.width)
        assertFalse(w.useTwoPanes)
        assertFalse(w.useTwoColumns)
    }

    @Test
    fun phoneLandscapeUsesTwoPanes() {
        assertTrue(MeowWindow.fromDp(852, 393).useTwoPanes)
        assertTrue(MeowWindow.fromDp(740, 360).useTwoPanes)
    }

    @Test
    fun tabletPortraitStaysSingleFlowButGetsTwoColumns() {
        val w = MeowWindow.fromDp(800, 1280)
        assertEquals(WidthClass.Medium, w.width)
        assertFalse(w.useTwoPanes)
        assertTrue(w.useTwoColumns)
    }

    @Test
    fun tabletLandscapeUsesTwoPanes() {
        val w = MeowWindow.fromDp(1280, 800)
        assertEquals(WidthClass.Expanded, w.width)
        assertTrue(w.useTwoPanes)
    }
}
