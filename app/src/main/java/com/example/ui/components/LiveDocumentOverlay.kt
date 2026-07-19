package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawPath
import androidx.compose.ui.graphics.drawscope.drawRect
import androidx.compose.ui.graphics.drawscope.drawCircle
import androidx.compose.ui.unit.dp

/**
 * Draws the detected document quadrilateral over the camera preview.
 * [points] are normalized (0f..1f); [aspectRatio] is previewWidth/previewHeight
 * so the overlay matches the preview's letterboxed content.
 */
@Composable
fun LiveDocumentOverlay(
    points: CropPoints?,
    detected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points == null) return@Canvas
            val w = size.width
            val h = size.height
            val accent = if (detected) Color(0xFF00E676) else Color.Yellow
            val tl = Offset(points.topLeft.x * w, points.topLeft.y * h)
            val tr = Offset(points.topRight.x * w, points.topRight.y * h)
            val br = Offset(points.bottomRight.x * w, points.bottomRight.y * h)
            val bl = Offset(points.bottomLeft.x * w, points.bottomLeft.y * h)
            val path = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
            }
            drawPath(path = path, color = accent.copy(alpha = 0.25f))
            drawPath(path = path, color = accent, style = Stroke(width = 4.dp.toPx()))
            listOf(tl, tr, br, bl).forEach {
                drawCircle(accent, radius = 8.dp.toPx(), center = it)
            }
        }
    }
}
