package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

object DocumentFilterProcessor {
    /**
     * Applies document enhancement filters to correct scanning issues like too dark, too light, or low contrast.
     */
    fun applyFilter(bitmap: Bitmap, filterType: String): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap

        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        
        val colorMatrix = ColorMatrix()
        when (filterType.uppercase()) {
            "LIGHTEN" -> {
                // Corrects over-dark document scans by boosting brightness
                colorMatrix.set(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 35f,  // Scale up r, add 35
                    0f, 1.2f, 0f, 0f, 35f,  // Scale up g, add 35
                    0f, 0f, 1.2f, 0f, 35f,  // Scale up b, add 35
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "DARKEN" -> {
                // Corrects washed-out / too light documents by reducing brightness and strengthening shadows
                colorMatrix.set(floatArrayOf(
                    0.85f, 0f, 0f, 0f, -15f,
                    0f, 0.85f, 0f, 0f, -15f,
                    0f, 0f, 0.85f, 0f, -15f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "HIGH_CONTRAST" -> {
                // Corrects low legibility by scaling contrast
                val contrast = 1.6f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                colorMatrix.set(floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "GREYSCALE" -> {
                // Desaturates completely for simple grayscale storage
                colorMatrix.setSaturation(0f)
            }
            "B_W" -> {
                // Crisp monochrome threshold effect for maximum OCR legibility
                colorMatrix.setSaturation(0f)
                val contrast = 3.5f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                val m = floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
                val temp = ColorMatrix()
                temp.set(m)
                colorMatrix.postConcat(temp)
            }
            else -> {
                // ORIGINAL - no transformation needed
                return bitmap
            }
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
}
