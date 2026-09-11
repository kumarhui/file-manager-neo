package org.fossify.filemanager.customtools.passport

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

enum class PassportBackground(
    val title: String,
    val startColor: Int,
    val endColor: Int? = null
) {
    SKY_BLUE(
        "Sky Blue",
        0xFF87CEEB.toInt()
    ),
    LIGHT_PINK(
        "Light Pink",
        0xFFFFD1DC.toInt()
    ),
    WHITE(
        "White",
        Color.WHITE
    ),
    BLUE(
        "Blue",
        0xFFB8E7FF.toInt(),
        0xFF4FA3D1.toInt()
    ),
    PINK(
        "Pink",
        0xFFFFD6E7.toInt(),
        0xFFB9DFFF.toInt()
    )
}

object PassportPhotoProcessor {

    suspend fun loadBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val src = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(src) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun removeBackground(
        bitmap: Bitmap
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()

            val segmenter = SubjectSegmentation.getClient(options)
            val input = InputImage.fromBitmap(bitmap, 0)

            val foreground = suspendCancellableCoroutine<Bitmap?> { continuation ->
                segmenter.process(input)
                    .addOnSuccessListener { result ->
                        continuation.resume(result.foregroundBitmap)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
            segmenter.close()
            foreground
        } catch (_: Exception) {
            null
        }
    }

    fun applyBackground(
        foreground: Bitmap,
        background: PassportBackground
    ): Bitmap {
        val output = Bitmap.createBitmap(
            foreground.width,
            foreground.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (background.endColor == null) {
            paint.color = background.startColor
        } else {
            paint.shader = LinearGradient(
                0f,
                0f,
                output.width.toFloat(),
                output.height.toFloat(),
                background.startColor,
                background.endColor,
                Shader.TileMode.CLAMP
            )
        }

        canvas.drawRect(
            0f,
            0f,
            output.width.toFloat(),
            output.height.toFloat(),
            paint
        )
        paint.shader = null

        canvas.drawBitmap(
            foreground,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        return output
    }

    /**
     * Generates a 300 DPI A4 Passport Grid:
     * 36 slots (6 cols x 6 rows)
     * Slot size = exactly 30 x 40 mm (no internal margin)
     * Gap = 2 mm
     * Only filled slots have outer border lines.
     */
    suspend fun createA4PassportSheet(
        slotsMap: Map<Int, Bitmap>
    ): Bitmap = withContext(Dispatchers.Default) {
        val dpi = 300f
        val mmToPx = dpi / 25.4f

        val sheetWidth = (210f * mmToPx).toInt()
        val sheetHeight = (297f * mmToPx).toInt()

        val photoW = (30f * mmToPx).toInt()
        val photoH = (40f * mmToPx).toInt()
        val gap = 2f * mmToPx

        val cols = 6
        val rows = 6

        val gridWidth = (cols * photoW) + ((cols - 1) * gap)
        val gridHeight = (rows * photoH) + ((rows - 1) * gap)

        val startX = (sheetWidth - gridWidth) / 2f
        val startY = (sheetHeight - gridHeight) / 2f

        val sheet = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f * mmToPx
            color = Color.BLACK
        }

        for (slotIndex in 1..36) {
            val idx = slotIndex - 1
            val col = idx % cols
            val row = idx / cols

            val x = startX + col * (photoW + gap)
            val y = startY + row * (photoH + gap)

            val photoBitmap = slotsMap[slotIndex]
            if (photoBitmap != null) {
                // Scales directly to full slot boundaries (no internal padding)
                val scaled = Bitmap.createScaledBitmap(photoBitmap, photoW, photoH, true)
                canvas.drawBitmap(scaled, x, y, null)

                // Only draw border for slots that have an image
                canvas.drawRect(x, y, x + photoW, y + photoH, borderPaint)

                if (scaled !== photoBitmap) {
                    scaled.recycle()
                }
            }
        }

        sheet
    }

    suspend fun saveToDownloads(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "Passport_A4_Grid_$timestamp.jpg"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = context.contentResolver.insert(collection, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveCacheFile(context: Context, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "passport_temp_share_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        file
    }
}
