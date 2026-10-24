package com.example.thisisabutton

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Scrolling line graph of used heap memory (MB), newest sample on the right. */
class MemoryGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Top of the graph's scale, in MB (the app's heap limit). */
    var maxMb: Float = 256f
        set(value) {
            field = maxOf(1f, value)
            invalidate()
        }

    var accent: Int = Color.parseColor("#2E7D32")
        set(value) {
            field = value
            updateShader()
            invalidate()
        }

    private val samples = FloatArray(MAX_SAMPLES)
    private var start = 0
    private var count = 0

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.card) }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#2C323D")
        strokeWidth = dp(1f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textSize = dp(11f)
        letterSpacing = 0.1f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_primary)
        textSize = dp(16f)
        isFakeBoldText = true
        textAlign = Paint.Align.RIGHT
    }
    private val maxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textSize = dp(10f)
        textAlign = Paint.Align.RIGHT
    }

    private val linePath = Path()
    private val fillPath = Path()

    private val plotTop get() = dp(44f)
    private val plotBottom get() = height - dp(14f)

    init {
        updateShader()
    }

    fun addSample(usedMb: Float) {
        if (count < MAX_SAMPLES) {
            samples[(start + count) % MAX_SAMPLES] = usedMb
            count++
        } else {
            samples[start] = usedMb
            start = (start + 1) % MAX_SAMPLES
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    private fun updateShader() {
        linePaint.color = accent
        if (height > 0) {
            fillPaint.shader = LinearGradient(
                0f, plotTop, 0f, plotBottom,
                withAlpha(accent, 130), withAlpha(accent, 0),
                Shader.TileMode.CLAMP,
            )
        }
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val left = dp(14f)
        val right = w - dp(14f)
        val top = plotTop
        val bottom = plotBottom

        canvas.drawRoundRect(0f, 0f, w, h, dp(16f), dp(16f), bgPaint)

        val latest = if (count > 0) samples[(start + count - 1) % MAX_SAMPLES] else 0f
        canvas.drawText(context.getString(R.string.graph_title), left, dp(28f), labelPaint)
        canvas.drawText("%d MB".format(latest.toInt()), right, dp(28f), valuePaint)

        for (i in 0..3) {
            val y = top + (bottom - top) * i / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        canvas.drawText("max %d MB".format(maxMb.toInt()), right, top + dp(13f), maxPaint)

        if (count < 2) return

        val step = (right - left) / (MAX_SAMPLES - 1)
        linePath.reset()
        fillPath.reset()
        for (i in 0 until count) {
            val v = samples[(start + i) % MAX_SAMPLES]
            val x = left + i * step
            val y = bottom - (v / maxMb).coerceIn(0f, 1f) * (bottom - top)
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(left + (count - 1) * step, bottom)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }

    private companion object {
        const val MAX_SAMPLES = 120 // 30 seconds at 4 samples per second
    }
}
