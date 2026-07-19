package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Draws the detected document quadrilateral over the camera preview.
 *
 * Coordinate mapping (the usual bug source):
 *  - [points] are NORMALIZED (0f..1f) in the analysis frame (after rotation).
 *  - The PreviewView uses ScaleType.FILL_CENTER, which scales the camera image
 *    with "cover" semantics: scale = max(boxW/frameW, boxH/frameH), then centers
 *    it, cropping the overflow. So a frame-normalized point maps to preview-box
 *    pixels as:
 *        px = boxW/2 + (norm.x - 0.5) * frameW * scale
 *        py = boxH/2 + (norm.y - 0.5) * frameH * scale
 *    [frameWidth]/[frameHeight] are the rotated analysis frame's dimensions.
 *    When no frame size is known we fall back to a plain 0..1 * box mapping.
 */
@Composable
fun LiveDocumentOverlay(
    points: CropPoints?,
    detected: Boolean,
    modifier: Modifier = Modifier,
    frameWidth: Int = 0,
    frameHeight: Int = 0,
    stable: Boolean = false
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points == null) return@Canvas

            val w = size.width
            val h = size.height

            val toPx: (Offset) -> Offset = if (frameWidth > 0 && frameHeight > 0) {
                val fw = frameWidth.toFloat()
                val fh = frameHeight.toFloat()
                val scale: Float = if (w / fw > h / fh) w / fw else h / fh
                { norm: Offset ->
                    Offset(
                        w / 2f + (norm.x - 0.5f) * fw * scale,
                        h / 2f + (norm.y - 0.5f) * fh * scale
                    )
                }
            } else {
                { norm: Offset -> Offset(norm.x * w, norm.y * h) }
            }

            val accent = when {
                stable -> Color(0xFF00E676) // green: about to auto-capture
                detected -> Color(0xFFFFB300) // amber: searching / stabilizing
                else -> Color.Yellow
            }

            val tl = toPx(points.topLeft)
            val tr = toPx(points.topRight)
            val br = toPx(points.bottomRight)
            val bl = toPx(points.bottomLeft)

            val documentPath = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
            }

            // Dim everything outside the detected document to guide the user.
            // Path.op(path1, path2, operation) stores the result in `this`, so:
            // outer = outer REMAINDER (outer - documentPath).
            val outer = Path().apply {
                moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(0f, h); close()
            }
            outer.op(outer, documentPath, PathOperation.Difference)
            drawPath(path = outer, color = Color.Black.copy(alpha = 0.35f))

            drawPath(path = documentPath, color = accent.copy(alpha = 0.25f))
            drawPath(path = documentPath, color = accent, style = Stroke(width = 4.dp.toPx()))

            listOf(tl, tr, br, bl).forEach {
                drawCircle(accent, radius = 8.dp.toPx(), center = it)
            }
        }
    }
}
