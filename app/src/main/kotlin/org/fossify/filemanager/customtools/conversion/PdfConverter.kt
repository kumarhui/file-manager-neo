package org.fossify.filemanager.customtools.conversion

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import org.fossify.filemanager.customtools.models.ConversionResult
import org.fossify.filemanager.customtools.storage.DownloadStorageHelper
import java.io.FileOutputStream
import kotlin.math.min

object PdfConverter {

    const val A4_WIDTH = 595
    const val A4_HEIGHT = 842

    fun convert(
        context: android.content.Context,
        imagePath: String
    ): ConversionResult {

        return try {

            val bitmap =
                BitmapFactory.decodeFile(imagePath)
                    ?: return ConversionResult(
                        false,
                        error = "Unable to open image"
                    )

            val document = PdfDocument()

            val pageInfo =
                PdfDocument.PageInfo.Builder(
                    A4_WIDTH,
                    A4_HEIGHT,
                    1
                ).create()

            val page =
                document.startPage(pageInfo)

            val canvas: Canvas = page.canvas

            canvas.drawColor(Color.WHITE)

            val margin = 24f

            val availableWidth =
                A4_WIDTH - margin * 2

            val availableHeight =
                A4_HEIGHT - margin * 2

            val scale = min(
                availableWidth / bitmap.width,
                availableHeight / bitmap.height
            )

            val width =
                bitmap.width * scale

            val height =
                bitmap.height * scale

            val left =
                (A4_WIDTH - width) / 2f

            val top =
                (A4_HEIGHT - height) / 2f

            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    left,
                    top,
                    left + width,
                    top + height
                ),
                Paint(Paint.ANTI_ALIAS_FLAG)
            )

            document.finishPage(page)

            val file =
                DownloadStorageHelper.getPrivateOutputFile(
                    context,
                    "converted_${System.currentTimeMillis()}.pdf"
                )

            FileOutputStream(file).use {
                document.writeTo(it)
            }

            document.close()
            bitmap.recycle()

            ConversionResult(
                success = true,
                outputPath = file.absolutePath
            )

        } catch (e: Exception) {

            ConversionResult(
                false,
                error = e.localizedMessage
                    ?: "PDF conversion failed"
            )
        }
    }
}
