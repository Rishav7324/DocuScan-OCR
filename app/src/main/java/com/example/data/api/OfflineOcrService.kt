package com.example.data.api

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device OCR engine backed by ML Kit's latin text recognizer.
 * Runs fully offline, no remote API or network calls.
 */
object OfflineOcrService {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun performOfflineOcr(bitmap: Bitmap, customTag: String = ""): String = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(inputImage).await()

        val tagLabel = if (customTag.isNotBlank()) " [Tag: $customTag]" else ""

        val textResult = buildString {
            append(visionText.text.trim())
            if (visionText.text.isBlank()) {
                append("[No text detected on this page]")
            }
        }

        "$textResult$tagLabel"
    }
}
