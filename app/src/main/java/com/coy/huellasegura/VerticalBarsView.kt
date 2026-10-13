package com.coy.huellasegura

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Gráfico simple de barras verticales: el valor encima de cada barra y su etiqueta debajo. */
class VerticalBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var data: List<Pair<String, Int>> = emptyList()
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.accent_green) }
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.brand_dark) }
    private val axisPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.field_border)
        strokeWidth = 1.5f * density
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textAlign = Paint.Align.CENTER
        textSize = 13f * scaledDensity
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = 11f * scaledDensity
    }

    /** La última barra (la semana en curso) se resalta. */
    fun setData(values: List<Pair<String, Int>>) {
        data = values
        contentDescription = values.joinToString(", ") { "${it.first}: ${it.second}" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val topPad = 24f * density
        val bottomPad = 26f * density
        val chartHeight = height - topPad - bottomPad
        val base = topPad + chartHeight
        val max = data.maxOf { it.second }.coerceAtLeast(1)
        val slot = width.toFloat() / data.size
        val barWidth = slot * 0.56f
        val radius = 6f * density

        canvas.drawLine(0f, base, width.toFloat(), base, axisPaint)
        data.forEachIndexed { index, (label, value) ->
            val left = index * slot + (slot - barWidth) / 2f
            val barHeight = chartHeight * value / max
            if (value > 0) {
                val paint = if (index == data.lastIndex) currentPaint else barPaint
                canvas.drawRoundRect(RectF(left, base - barHeight, left + barWidth, base), radius, radius, paint)
            }
            canvas.drawText(value.toString(), left + barWidth / 2f, base - barHeight - 6f * density, valuePaint)
            canvas.drawText(label, index * slot + slot / 2f, base + 17f * density, labelPaint)
        }
    }
}
