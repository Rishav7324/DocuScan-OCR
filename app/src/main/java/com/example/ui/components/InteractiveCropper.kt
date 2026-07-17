package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

// Normalized coordinates (0f to 1f) for the crop points
data class CropPoints(
    val topLeft: Offset = Offset(0.1f, 0.1f),
    val topRight: Offset = Offset(0.9f, 0.1f),
    val bottomRight: Offset = Offset(0.9f, 0.9f),
    val bottomLeft: Offset = Offset(0.1f, 0.9f)
)

@Composable
fun InteractiveCropper(
    bitmap: Bitmap,
    points: CropPoints,
    onPointsChanged: (CropPoints) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        // Match bitmap aspect ratio in container bounds
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        val scaleX = containerWidth / bitmapWidth
        val scaleY = containerHeight / bitmapHeight
        val scale = minOf(scaleX, scaleY)

        val drawWidth = bitmapWidth * scale
        val drawHeight = bitmapHeight * scale

        val offsetX = (containerWidth - drawWidth) / 2f
        val offsetY = (containerHeight - drawHeight) / 2f

        // Convert normalized points to view space
        val tl = Offset(offsetX + points.topLeft.x * drawWidth, offsetY + points.topLeft.y * drawHeight)
        val tr = Offset(offsetX + points.topRight.x * drawWidth, offsetY + points.topRight.y * drawHeight)
        val br = Offset(offsetX + points.bottomRight.x * drawWidth, offsetY + points.bottomRight.y * drawHeight)
        val bl = Offset(offsetX + points.bottomLeft.x * drawWidth, offsetY + points.bottomLeft.y * drawHeight)

        var activeHandle by remember { mutableStateOf<Int?>(null) } // 0:TL, 1:TR, 2:BR, 3:BL

        val handleRadius = 24.dp
        val handleRadiusPx = 48f

        fun findClosestHandle(touch: Offset): Int? {
            val dists = listOf(
                sqrt((touch.x - tl.x) * (touch.x - tl.x) + (touch.y - tl.y) * (touch.y - tl.y)),
                sqrt((touch.x - tr.x) * (touch.x - tr.x) + (touch.y - tr.y) * (touch.y - tr.y)),
                sqrt((touch.x - br.x) * (touch.x - br.x) + (touch.y - br.y) * (touch.y - br.y)),
                sqrt((touch.x - bl.x) * (touch.x - bl.x) + (touch.y - bl.y) * (touch.y - bl.y))
            )
            val minDist = dists.minOrNull() ?: Float.MAX_VALUE
            return if (minDist < 120f) {
                dists.indexOf(minDist)
            } else null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            activeHandle = findClosestHandle(startOffset)
                        },
                        onDragEnd = { activeHandle = null },
                        onDragCancel = { activeHandle = null },
                        onDrag = { change, dragAmount ->
                            val currentActive = activeHandle ?: return@detectDragGestures
                            change.consume()

                            // Get current point in view coordinates, apply delta
                            val currentViewPt = when (currentActive) {
                                0 -> tl
                                1 -> tr
                                2 -> br
                                3 -> bl
                                else -> return@detectDragGestures
                            } + dragAmount

                            // Clamp view coordinates inside the actual image canvas bounds
                            val clampedX = currentViewPt.x.coerceIn(offsetX, offsetX + drawWidth)
                            val clampedY = currentViewPt.y.coerceIn(offsetY, offsetY + drawHeight)

                            // Convert back to normalized 0..1 scale
                            val normX = (clampedX - offsetX) / drawWidth
                            val normY = (clampedY - offsetY) / drawHeight
                            val newOffset = Offset(normX, normY)

                            onPointsChanged(
                                when (currentActive) {
                                    0 -> points.copy(topLeft = newOffset)
                                    1 -> points.copy(topRight = newOffset)
                                    2 -> points.copy(bottomRight = newOffset)
                                    3 -> points.copy(bottomLeft = newOffset)
                                    else -> points
                                }
                            )
                        }
                    )
                }
        ) {
            // Draw background image and boundary guides
            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                // Draw the scaled bitmap
                val intOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt())
                val intSize = androidx.compose.ui.unit.IntSize(drawWidth.toInt(), drawHeight.toInt())
                
                // Represent original photo drawing in canvas
                drawContext.canvas.nativeCanvas.drawBitmap(
                    bitmap,
                    null,
                    android.graphics.Rect(
                        offsetX.toInt(),
                        offsetY.toInt(),
                        (offsetX + drawWidth).toInt(),
                        (offsetY + drawHeight).toInt()
                    ),
                    null
                )

                // Draw overlay semitransparent mask outside crop region
                // And path bounding lines
                val pathColor = Color(0xFF64B5F6)
                drawCircle(color = pathColor, radius = handleRadiusPx, center = tl)
                drawCircle(color = pathColor, radius = handleRadiusPx, center = tr)
                drawCircle(color = pathColor, radius = handleRadiusPx, center = br)
                drawCircle(color = pathColor, radius = handleRadiusPx, center = bl)

                // Outer circles
                drawCircle(color = Color.White, radius = handleRadiusPx - 10f, center = tl, style = Stroke(width = 4f))
                drawCircle(color = Color.White, radius = handleRadiusPx - 10f, center = tr, style = Stroke(width = 4f))
                drawCircle(color = Color.White, radius = handleRadiusPx - 10f, center = br, style = Stroke(width = 4f))
                drawCircle(color = Color.White, radius = handleRadiusPx - 10f, center = bl, style = Stroke(width = 4f))

                // Connect crop lines
                drawLine(color = pathColor, start = tl, end = tr, strokeWidth = 6f)
                drawLine(color = pathColor, start = tr, end = br, strokeWidth = 6f)
                drawLine(color = pathColor, start = br, end = bl, strokeWidth = 6f)
                drawLine(color = pathColor, start = bl, end = tl, strokeWidth = 6f)

                // Inner crosshairs
                drawLine(color = Color.White, start = Offset(tl.x - 20f, tl.y), end = Offset(tl.x + 20f, tl.y), strokeWidth = 3f)
                drawLine(color = Color.White, start = Offset(tl.x, tl.y - 20f), end = Offset(tl.x, tl.y + 20f), strokeWidth = 3f)

                drawLine(color = Color.White, start = Offset(tr.x - 20f, tr.y), end = Offset(tr.x + 20f, tr.y), strokeWidth = 3f)
                drawLine(color = Color.White, start = Offset(tr.x, tr.y - 20f), end = Offset(tr.x, tr.y + 20f), strokeWidth = 3f)

                drawLine(color = Color.White, start = Offset(br.x - 20f, br.y), end = Offset(br.x + 20f, br.y), strokeWidth = 3f)
                drawLine(color = Color.White, start = Offset(br.x, br.y - 20f), end = Offset(br.x, br.y + 20f), strokeWidth = 3f)

                drawLine(color = Color.White, start = Offset(bl.x - 20f, bl.y), end = Offset(bl.x + 20f, bl.y), strokeWidth = 3f)
                drawLine(color = Color.White, start = Offset(bl.x, bl.y - 20f), end = Offset(bl.x, bl.y + 20f), strokeWidth = 3f)
            }
        }
    }
}

/**
 * Warps a bitmap using a 2D Perspective Projective Transformation matrix.
 * Normalizes the 4 source points relative to original dimensions and maps to a rectangular canvas.
 */
fun performPerspectiveCorrection(bitmap: Bitmap, points: CropPoints): Bitmap {
    val srcPoints = floatArrayOf(
        points.topLeft.x * bitmap.width, points.topLeft.y * bitmap.height,
        points.topRight.x * bitmap.width, points.topRight.y * bitmap.height,
        points.bottomRight.x * bitmap.width, points.bottomRight.y * bitmap.height,
        points.bottomLeft.x * bitmap.width, points.bottomLeft.y * bitmap.height
    )

    // Calculate optimal destination dimensions based on top/bottom and left/right distances
    val widthA = sqrt((srcPoints[2] - srcPoints[0]) * (srcPoints[2] - srcPoints[0]) + (srcPoints[3] - srcPoints[1]) * (srcPoints[3] - srcPoints[1]))
    val widthB = sqrt((srcPoints[4] - srcPoints[6]) * (srcPoints[4] - srcPoints[6]) + (srcPoints[5] - srcPoints[7]) * (srcPoints[5] - srcPoints[7]))
    val targetWidth = maxOf(widthA, widthB).toInt().coerceIn(400, 4000)

    val heightA = sqrt((srcPoints[6] - srcPoints[0]) * (srcPoints[6] - srcPoints[0]) + (srcPoints[7] - srcPoints[1]) * (srcPoints[7] - srcPoints[1]))
    val heightB = sqrt((srcPoints[4] - srcPoints[2]) * (srcPoints[4] - srcPoints[2]) + (srcPoints[5] - srcPoints[3]) * (srcPoints[5] - srcPoints[3]))
    val targetHeight = maxOf(heightA, heightB).toInt().coerceIn(400, 5000)

    val dstPoints = floatArrayOf(
        0f, 0f,
        targetWidth.toFloat(), 0f,
        targetWidth.toFloat(), targetHeight.toFloat(),
        0f, targetHeight.toFloat()
    )

    val matrix = Matrix()
    matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

    val warpedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(warpedBitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(bitmap, matrix, paint)

    return warpedBitmap
}
