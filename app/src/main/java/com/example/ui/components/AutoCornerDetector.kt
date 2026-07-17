package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset

object AutoCornerDetector {
    /**
     * Scans the bitmap to find the most likely 4 corners of a document.
     * Returns a [CropPoints] containing normalized coordinates (0f..1f).
     */
    fun detectDocumentCorners(bitmap: Bitmap): CropPoints {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return CropPoints()

        // Let's sample the image to find the brightness threshold.
        // We will sample a 20x20 grid of pixels to find minimum and maximum brightness.
        var minBrightness = 255
        var maxBrightness = 0
        val sampleRows = 20
        val sampleCols = 20
        
        for (r in 0 until sampleRows) {
            val y = (height * r / sampleRows).coerceIn(0, height - 1)
            for (c in 0 until sampleCols) {
                val x = (width * c / sampleCols).coerceIn(0, width - 1)
                val pixel = bitmap.getPixel(x, y)
                val rVal = Color.red(pixel)
                val gVal = Color.green(pixel)
                val bVal = Color.blue(pixel)
                val brightness = (rVal + gVal + bVal) / 3
                if (brightness < minBrightness) minBrightness = brightness
                if (brightness > maxBrightness) maxBrightness = brightness
            }
        }

        // Calculate a threshold. Usually documents are light on dark.
        val threshold = (minBrightness + maxBrightness) / 2
        val paperIsBrighter = maxBrightness - threshold > threshold - minBrightness

        // Function to determine if a pixel is part of the document paper
        fun isDocumentPixel(x: Int, y: Int): Boolean {
            if (x !in 0 until width || y !in 0 until height) return false
            val pixel = bitmap.getPixel(x, y)
            val rVal = Color.red(pixel)
            val gVal = Color.green(pixel)
            val bVal = Color.blue(pixel)
            val brightness = (rVal + gVal + bVal) / 3
            return if (paperIsBrighter) {
                brightness >= threshold + 12
            } else {
                brightness <= threshold - 12
            }
        }

        val maxSteps = 150
        
        // 1. Scan for Top-Left corner from (0,0) diagonally towards (width * 0.45, height * 0.45)
        var tlOffset = Offset(0.08f, 0.08f)
        for (i in 0..maxSteps) {
            val t = i.toFloat() / maxSteps
            val x = (width * 0.45f * t).toInt().coerceIn(0, width - 1)
            val y = (height * 0.45f * t).toInt().coerceIn(0, height - 1)
            if (isDocumentPixel(x, y)) {
                tlOffset = Offset(x.toFloat() / width, y.toFloat() / height)
                break
            }
        }

        // 2. Scan for Top-Right corner from (width-1, 0) diagonally towards (width * 0.55, height * 0.45)
        var trOffset = Offset(0.92f, 0.08f)
        for (i in 0..maxSteps) {
            val t = i.toFloat() / maxSteps
            val x = (width - 1 - (width * 0.45f * t)).toInt().coerceIn(0, width - 1)
            val y = (height * 0.45f * t).toInt().coerceIn(0, height - 1)
            if (isDocumentPixel(x, y)) {
                trOffset = Offset(x.toFloat() / width, y.toFloat() / height)
                break
            }
        }

        // 3. Scan for Bottom-Right corner from (width-1, height-1) diagonally towards (width * 0.55, height * 0.55)
        var brOffset = Offset(0.92f, 0.92f)
        for (i in 0..maxSteps) {
            val t = i.toFloat() / maxSteps
            val x = (width - 1 - (width * 0.45f * t)).toInt().coerceIn(0, width - 1)
            val y = (height - 1 - (height * 0.45f * t)).toInt().coerceIn(0, height - 1)
            if (isDocumentPixel(x, y)) {
                brOffset = Offset(x.toFloat() / width, y.toFloat() / height)
                break
            }
        }

        // 4. Scan for Bottom-Left corner from (0, height-1) diagonally towards (width * 0.45, height * 0.55)
        var blOffset = Offset(0.08f, 0.92f)
        for (i in 0..maxSteps) {
            val t = i.toFloat() / maxSteps
            val x = (width * 0.45f * t).toInt().coerceIn(0, width - 1)
            val y = (height - 1 - (height * 0.45f * t)).toInt().coerceIn(0, height - 1)
            if (isDocumentPixel(x, y)) {
                blOffset = Offset(x.toFloat() / width, y.toFloat() / height)
                break
            }
        }

        // Guardrails to prevent collapsing points
        val minX = 0.02f
        val maxX = 0.98f
        val minY = 0.02f
        val maxY = 0.98f

        val tl = Offset(tlOffset.x.coerceIn(minX, 0.42f), tlOffset.y.coerceIn(minY, 0.42f))
        val tr = Offset(trOffset.x.coerceIn(0.58f, maxX), trOffset.y.coerceIn(minY, 0.42f))
        val br = Offset(brOffset.x.coerceIn(0.58f, maxX), brOffset.y.coerceIn(0.58f, maxY))
        val bl = Offset(blOffset.x.coerceIn(minX, 0.42f), blOffset.y.coerceIn(0.58f, maxY))

        return CropPoints(topLeft = tl, topRight = tr, bottomRight = br, bottomLeft = bl)
    }
}
