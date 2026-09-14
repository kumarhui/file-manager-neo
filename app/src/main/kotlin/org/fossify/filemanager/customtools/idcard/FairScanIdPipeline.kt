package org.fossify.filemanager.customtools.idcard

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object FairScanIdPipeline {

    suspend fun processDocument(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val baseBitmap = decodeSampledAndOriented(context, uri)

        // 1. Neural segmentation & perspective unwarp (with safety padding)
        val segmentationService = FairScanSegmentationService(context)
        val quad = segmentationService.detectQuad(baseBitmap)
        val unwarped = segmentationService.perspectiveWarp(baseBitmap, quad)

        // 2. Reading orientation detection via ML Kit text elements
        val oriented = correctReadingOrientation(unwarped)

        // 3. Magic Color enhancement (contrast 1.25x, brightness +10)
        applyMagicColor(oriented)
    }

    private fun decodeSampledAndOriented(context: Context, uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

        val maxEdge = 2048
        var sampleSize = 1
        while (options.outWidth / sampleSize > maxEdge || options.outHeight / sampleSize > maxEdge) {
            sampleSize *= 2
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        var bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalStateException("Failed to decode bitmap from URI")

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            }
        }
        return bmp
    }

    private suspend fun correctReadingOrientation(bitmap: Bitmap): Bitmap = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val maxDim = max(bitmap.width, bitmap.height)
        val scale = 800f / maxDim
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }

        val inputImage = InputImage.fromBitmap(scaled, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val angles = mutableListOf<Float>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {
                            angles.add(element.angle)
                        }
                    }
                }

                if (angles.isEmpty()) {
                    continuation.resumeWith(Result.success(bitmap))
                    return@addOnSuccessListener
                }

                val snapped = angles.map { angleVal: Float ->
                    val normalized = (angleVal % 360f + 360f) % 360f
                    when {
                        normalized in 45f..135f -> 90
                        normalized in 135f..225f -> 180
                        normalized in 225f..315f -> 270
                        else -> 0
                    }
                }

                val counts: Map<Int, Int> = snapped.groupingBy { it }.eachCount()
                val dominantAngle: Int = counts.maxByOrNull { entry -> entry.value }?.key ?: 0
                val rotation = (360 - dominantAngle) % 360

                if (rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    continuation.resumeWith(Result.success(rotated))
                } else {
                    continuation.resumeWith(Result.success(bitmap))
                }
            }
            .addOnFailureListener {
                continuation.resumeWith(Result.success(bitmap))
            }
    }

    private fun applyMagicColor(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val contrast = 1.25f
        val brightness = 10f
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    suspend fun generateA4CompositeSheet(context: Context, front: Bitmap?, back: Bitmap?): Bitmap = withContext(Dispatchers.Default) {
        val a4Width = 2480
        val a4Height = 3508
        val cardWidth = 1011
        val cardHeight = 638
        val marginX = (a4Width - (cardWidth * 2)) / 3
        val marginTop = 120

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val a4Bitmap = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(a4Bitmap)
        canvas.drawColor(Color.WHITE)

        front?.let { bmp ->
            val dstRect = Rect(marginX, marginTop, marginX + cardWidth, marginTop + cardHeight)
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
            canvas.drawRect(RectF(dstRect), borderPaint)
        }

        back?.let { bmp ->
            val leftPos2 = marginX * 2 + cardWidth
            val dstRect = Rect(leftPos2, marginTop, leftPos2 + cardWidth, marginTop + cardHeight)
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
            canvas.drawRect(RectF(dstRect), borderPaint)
        }

        a4Bitmap
    }
}