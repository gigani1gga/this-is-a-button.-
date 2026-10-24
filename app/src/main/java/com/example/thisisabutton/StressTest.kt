package com.example.thisisabutton

import android.os.SystemClock
import java.util.concurrent.atomic.LongAdder

private const val MB = 1024 * 1024

/** One of the four stress levels. Levels 1-3 are timed; level 4 runs until the app crashes. */
enum class Level(
    val number: Int,
    val nameRes: Int,
    val colorRes: Int,
    private val cpuFactor: Float,
    private val heapFraction: Float,
    val durationSec: Int,
    val crashes: Boolean,
) {
    LIGHT(1, R.string.level_1_name, R.color.level1, 0.25f, 0.20f, 10, false),
    MEDIUM(2, R.string.level_2_name, R.color.level2, 1f, 0.40f, 10, false),
    HEAVY(3, R.string.level_3_name, R.color.level3, 2f, 0.65f, 10, false),
    EXTREME(4, R.string.level_4_name, R.color.level4, 4f, 1f, 0, true);

    val threads: Int
        get() = maxOf(1, (Runtime.getRuntime().availableProcessors() * cpuFactor).toInt())

    val targetMb: Long
        get() = (Runtime.getRuntime().maxMemory() * heapFraction / MB).toLong()
}

/**
 * Loads CPU + heap memory inside this app's own process.
 * Levels 1-3 release everything on stop(); level 4 throws on purpose when memory runs out.
 */
class StressTest(val level: Level, private val crashMessage: String) {

    @Volatile private var running = false
    @Volatile private var sink = 0.0
    private val ops = LongAdder()
    private var startedAt = 0L
    private var stoppedAt = 0L

    val elapsedMs: Long
        get() = when {
            startedAt == 0L -> 0L
            stoppedAt != 0L -> stoppedAt - startedAt
            else -> SystemClock.elapsedRealtime() - startedAt
        }

    /** Average throughput so far, in millions of loop iterations per second. */
    val opsPerSecondM: Double
        get() {
            val seconds = elapsedMs / 1000.0
            return if (seconds <= 0.0) 0.0 else ops.sum() / seconds / 1e6
        }

    fun start() {
        if (running) return
        running = true
        startedAt = SystemClock.elapsedRealtime()
        repeat(level.threads) { spawn { cpuLoop() } }
        spawn { memoryLoop() }
        if (level.crashes) {
            // Guarantees the crash even if the heap is huge and OOM is slow.
            spawn {
                Thread.sleep(CRASH_DEADLINE_MS)
                if (running) crash()
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun spawn(block: () -> Unit) {
        Thread { block() }.apply { isDaemon = true }.start()
    }

    private fun cpuLoop() {
        var x = 0.0
        while (running) {
            repeat(BATCH) { x += Math.sqrt(x + 1.0) * Math.sin(x) }
            sink = x
            ops.add(BATCH.toLong())
        }
    }

    private fun memoryLoop() {
        val hog = ArrayList<ByteArray>()
        val targetChunks = level.targetMb / CHUNK_MB
        var full = false

        while (running) {
            if (!full && (level.crashes || hog.size < targetChunks)) {
                try {
                    hog.add(ByteArray(CHUNK_MB * MB))
                } catch (e: OutOfMemoryError) {
                    if (level.crashes) {
                        hog.clear()
                        crash()
                    }
                    // Back off a little so the UI keeps enough room to run.
                    repeat(2) { if (hog.isNotEmpty()) hog.removeAt(hog.size - 1) }
                    full = true
                }
            }
            Thread.sleep(if (level.crashes) 5L else 25L)
        }

        hog.clear()
        System.gc()
    }

    private fun crash(): Nothing = throw RuntimeException(crashMessage)

    private companion object {
        const val BATCH = 20_000
        const val CHUNK_MB = 8
        const val CRASH_DEADLINE_MS = 12_000L
    }
}
