package com.salihraphics.meow

import android.app.Application
import android.os.StrictMode
import com.salihraphics.meow.core.diagnostics.CrashLog
import java.io.File

class MeowApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Crash and freeze records on the device (Ayarlar → Hata kayıtları). */
    lateinit var crashLog: CrashLog
        private set

    override fun onCreate() {
        super.onCreate()
        crashLog = CrashLog(File(filesDir, "diagnostics"), BuildConfig.VERSION_NAME).also { it.install() }
        if (BuildConfig.DEBUG) {
            // Debug builds report work on the UI thread (disk, network) in logcat, to catch freezes.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        }
        container = AppContainer(this)
        container.answerKeyScheduler.start()
        // Off the UI thread: reading the system's exit records touches the disk.
        Thread({ crashLog.collectExitReasons(this) }, "meow-diagnostics").start()
    }
}
