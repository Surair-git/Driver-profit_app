package com.surair.driverprofit.tracking

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.surair.driverprofit.core.OcrLine

/** Reconocimiento de texto en el celular, sin internet. */
object Ocr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun recognize(bitmap: Bitmap, onResult: (List<OcrLine>) -> Unit, onError: (Exception) -> Unit = {}) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    line.boundingBox?.let { b -> OcrLine(line.text, b.left, b.top, b.right, b.bottom) }
                }
                onResult(lines)
            }
            .addOnFailureListener { onError(it) }
    }
}
