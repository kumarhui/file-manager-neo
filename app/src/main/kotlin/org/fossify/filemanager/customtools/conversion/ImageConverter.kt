package org.fossify.filemanager.customtools.conversion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import org.fossify.filemanager.customtools.models.ConversionResult
import org.fossify.filemanager.customtools.storage.DownloadStorageHelper
import java.io.FileOutputStream

object ImageConverter {

    enum class Format(
        val extension: String,
        val mimeType: String
    ) {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp")
    }

    fun convert(
        context: android.content.Context,
        inputPath: String,
        format: Format,
        quality: Int = 95
    ): ConversionResult {

        return try {

            val bitmap =
                BitmapFactory.decodeFile(inputPath)
                    ?: return ConversionResult(
                        success = false,
                        error = "Unable to open image"
                    )

            val file =
                DownloadStorageHelper.getPrivateOutputFile(
                    context,
                    "converted_${System.currentTimeMillis()}.${format.extension}"
                )

            val compressionFormat =
                when (format) {

                    Format.JPEG ->
                        Bitmap.CompressFormat.JPEG

                    Format.PNG ->
                        Bitmap.CompressFormat.PNG

                    Format.WEBP -> {
                        if (Build.VERSION.SDK_INT >= 30) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            Bitmap.CompressFormat.WEBP
                        }
                    }
                }

            FileOutputStream(file).use { output ->

                bitmap.compress(
                    compressionFormat,
                    quality.coerceIn(1, 100),
                    output
                )
            }

            bitmap.recycle()

            ConversionResult(
                success = true,
                outputPath = file.absolutePath
            )

        } catch (e: Exception) {

            ConversionResult(
                success = false,
                error = e.localizedMessage
                    ?: "Image conversion failed"
            )
        }
    }

    fun compress(
        context: android.content.Context,
        inputPath: String,
        quality: Int
    ): ConversionResult {

        return try {

            val bitmap =
                BitmapFactory.decodeFile(
                    inputPath
                ) ?: return ConversionResult(
                    success = false,
                    error = "Unable to open image"
                )

            val file =
                DownloadStorageHelper
                    .getPrivateOutputFile(
                        context,
                        "compressed_${System.currentTimeMillis()}.jpg"
                    )

            FileOutputStream(file).use { output ->

                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    quality.coerceIn(1, 100),
                    output
                )
            }

            bitmap.recycle()

            ConversionResult(
                success = true,
                outputPath = file.absolutePath
            )

        } catch (e: Exception) {

            ConversionResult(
                success = false,
                error =
                    e.localizedMessage
                        ?: "Image compression failed"
            )
        }
    }
    fun estimateCompressedSize(
        inputPath: String,
        quality: Int
    ): Long? {

        return try {

            val bitmap =
                BitmapFactory.decodeFile(
                    inputPath
                ) ?: return null

            val stream =
                java.io.ByteArrayOutputStream()

            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                quality.coerceIn(1, 100),
                stream
            )

            bitmap.recycle()

            stream.size().toLong()

        } catch (_: Exception) {

            null
        }
    }
}
