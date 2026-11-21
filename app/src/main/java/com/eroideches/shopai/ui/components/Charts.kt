package com.eroideches.shopai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.data.PricePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Grafico dello storico prezzi (a gradini: il prezzo resta uguale fino al cambio successivo). */
@Composable
fun PriceChart(points: List<PricePoint>, target: Long?, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val targetColor = MaterialTheme.colorScheme.tertiary
    if (points.isEmpty()) return
    val pts = if (points.size == 1) points + points.first().copy(at = System.currentTimeMillis()) else points + points.last().copy(at = maxOf(System.currentTimeMillis(), points.last().at))
    val minV = minOf(pts.minOf { it.cents }, target ?: Long.MAX_VALUE).toFloat()
    val maxV = maxOf(pts.maxOf { it.cents }, target ?: 0).toFloat()
    val span = (maxV - minV).coerceAtLeast(maxV * 0.05f + 1f)
    val t0 = pts.first().at.toFloat()
    val tSpan = (pts.last().at - pts.first().at).toFloat().coerceAtLeast(1f)
    val fmt = SimpleDateFormat("d MMM", Locale.ITALIAN)

    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text("Min ${Money(pts.minOf { it.cents }).format()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text("Max ${Money(pts.maxOf { it.cents }).format()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val pad = 8.dp.toPx()
                val w = size.width; val h = size.height - pad * 2
                fun x(t: Long) = (t - t0) / tSpan * w
                fun y(v: Long) = pad + h - (v - minV) / span * h
                for (i in 0..3) {
                    val gy = pad + h * i / 3f
                    drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                }
                val path = Path()
                pts.forEachIndexed { i, p ->
                    val px = x(p.at); val py = y(p.cents)
                    if (i == 0) path.moveTo(px, py) else { path.lineTo(px, y(pts[i - 1].cents)); path.lineTo(px, py) }
                }
                val fill = Path().apply { addPath(path); lineTo(w, size.height); lineTo(0f, size.height); close() }
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), Color.Transparent)))
                drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                target?.let {
                    val ty = y(it)
                    drawLine(targetColor, Offset(0f, ty), Offset(w, ty), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                }
                drawCircle(line, radius = 5.dp.toPx(), center = Offset(x(pts.last().at), y(pts.last().cents)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmt.format(Date(pts.first().at)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (target != null) Text("- - avviso ${Money(target).format()}", style = MaterialTheme.typography.labelSmall, color = targetColor)
            Spacer(Modifier.weight(1f))
            Text("oggi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Sparkline(points: List<PricePoint>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (points.size < 2) return
    Canvas(modifier) {
        val minV = points.minOf { it.cents }.toFloat(); val maxV = points.maxOf { it.cents }.toFloat()
        val span = (maxV - minV).coerceAtLeast(1f)
        val step = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, p ->
            val px = i * step; val py = size.height - (p.cents - minV) / span * size.height
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
