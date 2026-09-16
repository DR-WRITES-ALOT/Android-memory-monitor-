package com.example.memorymonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Minimal used-% line chart (0..100 on Y). No library, ~40 lines. */
@Composable
fun SimpleLineChart(values: List<Float>, maxPoints: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width
        val h = size.height

        // frame + 50 % / 85 % guide lines
        drawRect(Color.Gray, style = Stroke(1f))
        listOf(50f, 85f).forEach { pct ->
            val y = h - (pct / 100f) * h
            drawLine(Color.LightGray, Offset(0f, y), Offset(w, y), 1f)
        }

        if (values.size < 2) return@Canvas
        val stepX = w / (maxPoints - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = h - (v.coerceIn(0f, 100f) / 100f) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF1565C0), style = Stroke(3f))
    }
}
