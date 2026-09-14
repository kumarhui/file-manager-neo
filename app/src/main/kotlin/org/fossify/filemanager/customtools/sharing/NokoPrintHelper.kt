package org.fossify.filemanager.customtools.sharing

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.filemanager.customtools.pdfunlocker.PdfUnlockerDialog
import java.io.File
import java.io.FileOutputStream

object NokoPrintHelper {

    fun print(context: Context, imagePath: String): Boolean {
        return print(context, listOf(imagePath))
    }

    fun print(context: Context, paths: List<String>): Boolean {
        if (paths.isEmpty()) return false

        try {
            val uris = ArrayList<Uri>()
            for (path in paths) {
                val file = File(path)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    uris.add(uri)
                }
            }

            if (uris.isEmpty()) return false

            val hasPdf = paths.any { it.endsWith(".pdf", ignoreCase = true) }
            val hasImage = paths.any { !it.endsWith(".pdf", ignoreCase = true) }
            val mimeType = when {
                hasPdf && !hasImage -> "application/pdf"
                !hasPdf && hasImage -> "image/*"
                else -> "*/*"
            }

            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            intent.setPackage("com.noco.print")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                intent.setPackage("com.nokoprint")
                context.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "NokoPrint not installed or failed", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    fun handlePrintWorkflow(context: Context, paths: List<String>) {
        val pdfs = paths.filter { it.endsWith(".pdf", ignoreCase = true) }

        // If no PDFs or single item, directly pass to print
        if (pdfs.isEmpty() || paths.size == 1) {
            print(context, paths)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            // Check for password protection
            val lockedPdf = withContext(Dispatchers.IO) {
                pdfs.firstOrNull { isPdfPasswordProtected(it) }
            }

            if (lockedPdf != null) {
                AlertDialog.Builder(context)
                    .setTitle("Password Protected PDF Detected")
                    .setMessage("The file \"${File(lockedPdf).name}\" is password protected. Would you like to unlock it using PDF Unlocker first?")
                    .setPositiveButton("Unlock PDF") { _, _ ->
                        PdfUnlockerDialog.show(context, Uri.fromFile(File(lockedPdf)))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@launch
            }

            // If all files are PDFs and there are multiple, merge them before sending
            if (pdfs.size == paths.size) {
                val mergedPath = withContext(Dispatchers.IO) {
                    mergePdfFiles(context, pdfs)
                }

                if (mergedPath != null) {
                    print(context, listOf(mergedPath))
                } else {
                    print(context, paths)
                }
            } else {
                print(context, paths)
            }
        }
    }

    private fun isPdfPasswordProtected(path: String): Boolean {
        return try {
            val file = File(path)
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(pfd)
                renderer.close()
                false
            } catch (e: SecurityException) {
                true
            } finally {
                pfd.close()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun mergePdfFiles(context: Context, pdfPaths: List<String>): String? {
        return try {
            val outputDocument = PdfDocument()
            var pageIndex = 0

            for (path in pdfPaths) {
                val file = File(path)
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageIndex + 1).create()
                    val newPage = outputDocument.startPage(pageInfo)

                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    newPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    outputDocument.finishPage(newPage)

                    bitmap.recycle()
                    page.close()
                    pageIndex++
                }

                renderer.close()
                pfd.close()
            }

            val cacheFolder = File(context.cacheDir, "temp_merged_pdf").apply { mkdirs() }
            val mergedFile = File(cacheFolder, "Merged_Print_${System.currentTimeMillis()}.pdf")
            FileOutputStream(mergedFile).use { out ->
                outputDocument.writeTo(out)
            }
            outputDocument.close()

            mergedFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun printUri(context: Context, uri: Uri, mimeType: String = "application/pdf"): Boolean {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            intent.setPackage("com.nokoprint")
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                intent.setPackage("com.noco.print")
                context.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "NokoPrint not installed or failed: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}