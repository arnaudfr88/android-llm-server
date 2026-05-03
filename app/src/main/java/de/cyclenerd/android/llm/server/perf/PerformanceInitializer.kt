package de.cyclenerd.android.llm.server.perf

import android.content.Context
import androidx.startup.Initializer
import androidx.tracing.Trace
import de.cyclenerd.android.llm.server.utils.Logger

/**
 * App-startup hook that runs **before** the first Activity is created.
 *
 * Wired into [androidx.startup.InitializationProvider] via the manifest.
 * The provider is created during application bind, which happens before
 * `Application.onCreate()` returns — meaning every CPU/GPU optimisation
 * we apply here is in effect by the time the very first frame is drawn.
 *
 * Why use App Startup instead of just `Application.onCreate()`?
 * - Single, deterministic order across every dependency that wants to do
 *   early work (Compose, WorkManager, our own perf hooks).
 * - Lazy library init for libraries that need it later.
 * - No extra ContentProviders fighting for startup time.
 *
 * Doing it this early matters because the first frame of the launcher's
 * window animation also runs in our process — we want the GPU clocked
 * up before that frame is composed so the animation is buttery smooth.
 */
class PerformanceInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Trace.beginSection("PerformanceInitializer#create")
        try {
            Logger.i(TAG, "PerformanceInitializer running before Application.onCreate()")
            PerformanceManager.applyProcessWide(context)
        } finally {
            Trace.endSection()
        }
    }

    /** No dependencies — we want to be the FIRST thing that runs. */
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    private companion object {
        private const val TAG = "PerformanceInitializer"
    }
}
