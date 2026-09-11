package org.fossify.filemanager.customtools.conversion

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

object PdfPageExtractor {

    enum class Destination { SAME_FOLDER, NEW_FOLDER }

    fun extract(
        pdfPaths: List<String>,
        destination: Destination
    ): List<String> {
        val outputs = mutableListOf<String>()

        pdfPaths.map(::File)
            .filter { it.exists() && it.isFile && it.extension.equals("pdf", true) }
            .forEach { pdf ->
                val outputDir = when (destination) {
                    Destination.SAME_FOLDER -> pdf.parentFile ?: return@forEach
                    Destination.NEW_FOLDER -> File(pdf.parentFile ?: return@forEach, "${pdf.nameWithoutExtension}_pages").also { it.mkdirs() }
                }

                if (!outputDir.exists() && !outputDir.mkdirs()) return@forEach

                val descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(descriptor)
                try {
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val scale = 2f
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val output = uniqueFile(outputDir, "${pdf.nameWithoutExtension}_page_${String.format("%03d", index + 1)}.png")
                            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bitmap.recycle()
                            outputs += output.absolutePath
                        }
                    }
                } finally {
                    renderer.close()
                    descriptor.close()
                }
            }

        return outputs
    }

    private fun uniqueFile(directory: File, name: String): File {
        val original = File(directory, name)
        if (!original.exists()) return original

        val base = original.nameWithoutExtension
        val ext = original.extension
        var i = 1
        while (true) {
            val candidate = File(directory, "${base}_$i.$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }
}
