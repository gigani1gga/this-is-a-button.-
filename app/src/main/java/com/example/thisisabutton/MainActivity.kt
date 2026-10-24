package com.example.thisisabutton

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val runtime = Runtime.getRuntime()
    private val prefs by lazy { getSharedPreferences("scores", MODE_PRIVATE) }

    private var test: StressTest? = null

    private lateinit var graph: MemoryGraphView
    private lateinit var statHeap: TextView
    private lateinit var statThreads: TextView
    private lateinit var statScore: TextView
    private lateinit var status: TextView
    private val buttons = LinkedHashMap<Level, Button>()

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, SAMPLE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        graph = findViewById(R.id.graph)
        statHeap = findViewById(R.id.statHeap)
        statThreads = findViewById(R.id.statThreads)
        statScore = findViewById(R.id.statScore)
        status = findViewById(R.id.status)

        graph.maxMb = (runtime.maxMemory() / MB).toFloat()
        graph.accent = getColor(Level.LIGHT.colorRes)
        statScore.text = getString(R.string.stat_score, 0.0)

        val ids = mapOf(
            Level.LIGHT to R.id.level1,
            Level.MEDIUM to R.id.level2,
            Level.HEAVY to R.id.level3,
            Level.EXTREME to R.id.level4,
        )
        for ((level, id) in ids) {
            val button = findViewById<Button>(id)
            button.setOnClickListener { onLevelClicked(level) }
            buttons[level] = button
        }
        refreshUi()
    }

    override fun onStart() {
        super.onStart()
        handler.post(tick)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tick)
        // Levels 1-3 never keep running in the background.
        if (test?.level?.crashes != true) finishTest(completed = false)
    }

    private fun onLevelClicked(level: Level) {
        val running = test
        if (running != null) {
            if (running.level == level && !level.crashes) finishTest(completed = false)
            return
        }
        val t = StressTest(level, getString(R.string.crash_message))
        test = t
        graph.accent = getColor(level.colorRes)
        statThreads.text = getString(R.string.stat_threads, level.threads)
        t.start()
        refreshUi()
    }

    private fun onTick() {
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / MB
        graph.addSample(usedMb.toFloat())
        statHeap.text = getString(R.string.stat_heap, usedMb, runtime.maxMemory() / MB)

        val t = test ?: return
        statScore.text = getString(R.string.stat_score, t.opsPerSecondM)
        if (t.level.crashes) {
            status.setText(R.string.status_crashing)
        } else if (t.elapsedMs >= t.level.durationSec * 1000L) {
            finishTest(completed = true)
        } else {
            val left = t.level.durationSec - (t.elapsedMs / 1000).toInt()
            status.text = getString(R.string.status_running, t.level.number, maxOf(left, 0))
        }
    }

    private fun finishTest(completed: Boolean) {
        val t = test ?: return
        t.stop()
        test = null

        if (completed) {
            val score = t.opsPerSecondM
            val key = "best_${t.level.number}"
            val isBest = score > prefs.getFloat(key, 0f)
            if (isBest) prefs.edit().putFloat(key, score.toFloat()).apply()
            statScore.text = getString(R.string.stat_score, score)
            status.text = getString(
                if (isBest) R.string.status_done_best else R.string.status_done,
                t.level.number,
                score,
            )
        } else {
            status.setText(R.string.status_stopped)
        }
        refreshUi()
    }

    private fun refreshUi() {
        val active = test
        for ((level, button) in buttons) {
            val isActive = active?.level == level
            val line2 = when {
                isActive && level.crashes -> getString(R.string.running_crash)
                isActive -> getString(R.string.tap_to_stop)
                level.crashes -> getString(R.string.level_desc_crash, level.threads)
                else -> {
                    val desc = getString(R.string.level_desc, level.threads, level.targetMb, level.durationSec)
                    val best = prefs.getFloat("best_${level.number}", 0f)
                    if (best > 0f) desc + " · " + getString(R.string.best_suffix, best) else desc
                }
            }
            button.text = styled(level, line2)
            button.isEnabled = if (isActive) !level.crashes else active == null
            button.alpha = if (active != null && !isActive) 0.4f else 1f
        }
    }

    private fun styled(level: Level, line2: String): CharSequence {
        val title = getString(R.string.level_title, level.number, getString(level.nameRes))
        return SpannableStringBuilder().apply {
            append(title)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(18, true), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append("\n")
            val from = length
            append(line2)
            setSpan(AbsoluteSizeSpan(13, true), from, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private companion object {
        const val MB = 1024 * 1024
        const val SAMPLE_MS = 250L
    }
}
