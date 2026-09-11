package org.fossify.filemanager.customtools

import android.content.Context
import android.graphics.BitmapFactory
import org.fossify.filemanager.customtools.conversion.PdfConverter
import org.fossify.filemanager.customtools.layout.IdCardLayoutEngine
import java.io.File

object CustomToolRunner {

    fun createIdCard(
        context: Context,
        imagePath: String
    ): String? {

        val sourceFile = File(imagePath)

        if (!sourceFile.exists()) {
            return null
        }

        val source =
            BitmapFactory.decodeFile(
                sourceFile.absolutePath
            ) ?: return null

        return try {

            val output =
                IdCardLayoutEngine.createA4Sheet(
                    source
                )

            val directory =
                File(
                    context.cacheDir,
                    "custom_tools"
                )

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val outputFile =
                File(
                    directory,
                    "id_card_${System.currentTimeMillis()}.png"
                )

            outputFile.outputStream().use { stream ->

                output.compress(
                    android.graphics.Bitmap.CompressFormat.PNG,
                    100,
                    stream
                )
            }

            output.recycle()

            outputFile.absolutePath

        } finally {

            source.recycle()
        }
    }

    fun convertPdf(
        context: Context,
        imagePath: String
    ) =
        PdfConverter.convert(
            context,
            imagePath
        )
}
