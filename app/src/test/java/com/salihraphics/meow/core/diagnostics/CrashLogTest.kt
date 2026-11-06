package com.salihraphics.meow.core.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashLogTest {
    @get:Rule val tmp = TemporaryFolder()
    private val original = Thread.getDefaultUncaughtExceptionHandler()

    @After fun restore() = Thread.setDefaultUncaughtExceptionHandler(original)

    @Test
    fun aCrashIsWrittenDownAndStillReachesTheSystemHandler() {
        var handed: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> handed = e }
        val log = CrashLog(tmp.root, "0.1.0")
        log.install()
        val boom = IllegalArgumentException("Cannot round NaN value.")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), boom)
        assertSame("not swallowed", boom, handed)
        val text = log.entries().single().readText()
        assertTrue(text.contains("Cannot round NaN value."))
        assertTrue(text.contains("CrashLogTest"))
    }

    @Test
    fun onlyTheNewestTenAreKept() {
        val log = CrashLog(tmp.root, "0.1.0")
        repeat(14) { log.write("crash", "Çökme $it", "trace $it", at = 1_700_000_000_000L + it * 1_000) }
        val entries = log.entries()
        assertEquals(CrashLog.MAX_FILES, entries.size)
        assertTrue(entries.first().readText().contains("trace 13"))
        log.clear()
        assertTrue(log.entries().isEmpty())
    }
}
