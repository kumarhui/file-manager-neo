package org.fossify.filemanager.customtools.conversion

import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import java.io.File

object PrintPdfCombiner {

    fun combine(
        filePaths: List<String>,
        outputFile: File
    ): Boolean {

        if (filePaths.isEmpty()) {
            return false
        }

        return try {

            outputFile.parentFile?.mkdirs()

            val writer = PdfWriter(outputFile)
            val destinationPdf = PdfDocument(writer)
            val document = Document(destinationPdf)

            for (path in filePaths) {

                val file = File(path)

                if (!file.exists() || !file.isFile) {
                    continue
                }

                when (file.extension.lowercase()) {

                    "jpg",
                    "jpeg",
                    "png",
                    "webp" -> {

                        addImage(
                            document,
                            file
                        )
                    }

                    "pdf" -> {

                        mergePdf(
                            destinationPdf,
                            file
                        )
                    }
                }
            }

            document.close()

            outputFile.exists() &&
                    outputFile.length() > 0

        } catch (_: Exception) {

            try {
                if (outputFile.exists()) {
                    outputFile.delete()
                }
            } catch (_: Exception) {
            }

            false
        }
    }

    private fun addImage(
        document: Document,
        file: File
    ) {

        val imageData =
            ImageDataFactory.create(
                file.absolutePath
            )

        val image = Image(imageData)

        val pageSize = PageSize.A4

        val pageWidth = pageSize.width
        val pageHeight = pageSize.height

        val margin = 24f

        val maxWidth =
            pageWidth - (margin * 2)

        val maxHeight =
            pageHeight - (margin * 2)

        image.scaleToFit(
            maxWidth,
            maxHeight
        )

        image.setFixedPosition(
            margin + (maxWidth - image.imageScaledWidth) / 2f,
            margin + (maxHeight - image.imageScaledHeight) / 2f
        )

        document.add(
            image
        )

        document.add(
            com.itextpdf.layout.element.AreaBreak()
        )
    }

    private fun mergePdf(
        destinationPdf: PdfDocument,
        sourceFile: File
    ) {

        val reader =
            PdfReader(
                sourceFile.absolutePath
            )

        val sourcePdf =
            PdfDocument(reader)

        try {

            sourcePdf.copyPagesTo(
                1,
                sourcePdf.numberOfPages,
                destinationPdf
            )

        } finally {

            sourcePdf.close()
        }
    }
}