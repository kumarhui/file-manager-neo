package org.fossify.filemanager.customtools.sharing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object NokoPrintHelper {

    fun print(context: Context, imagePath: String): Boolean {
        return print(context, listOf(imagePath))
    }

    fun print(context: Context, imagePaths: List<String>): Boolean {
        if (imagePaths.isEmpty()) return false

        try {
            val uris = ArrayList<Uri>()
            for (path in imagePaths) {
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

            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
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

    fun extractPdfToTempAndPrint(context: Context, pdfPaths: List<String>, targetDpi: Int = 300) {
        val progressLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(60, 48, 60, 48)
            gravity = android.view.Gravity.CENTER_VERTICAL
            val progressBar = android.widget.ProgressBar(context).apply {
                isIndeterminate = true
            }
            val textView = android.widget.TextView(context).apply {
                text = "Rendering pages at ${targetDpi} DPI..."
                textSize = 15f
                setPadding(36, 0, 0, 0)
                setTextColor(android.graphics.Color.BLACK)
            }
            addView(progressBar)
            addView(textView)
        }

        val progressDialog = android.app.AlertDialog.Builder(context)
            .setView(progressLayout)
            .setCancelable(false)
            .create()

        progressDialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            var currentDpi = targetDpi
            var success = false
            var extractedImagePaths = emptyList<String>()

            while (!success && currentDpi >= 150) {
                try {
                    extractedImagePaths = withContext(Dispatchers.IO) {
                        val outputPaths = mutableListOf<String>()
                        val cacheFolder = File(context.cacheDir, "temp_print_pages").apply { mkdirs() }

                        pdfPaths.forEach { path ->
                            val file = File(path)
                            if (file.exists() && path.endsWith(".pdf", ignoreCase = true)) {
                                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                                val renderer = PdfRenderer(pfd)
                                val scaleFactor = currentDpi.toFloat() / 72f

                                for (i in 0 until renderer.pageCount) {
                                    val page = renderer.openPage(i)
                                    val renderWidth = (page.width * scaleFactor).toInt()
                                    val renderHeight = (page.height * scaleFactor).toInt()

                                    val bitmap = Bitmap.createBitmap(
                                        renderWidth,
                                        renderHeight,
                                        Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = android.graphics.Canvas(bitmap)
                                    canvas.drawColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    page.close()

                                    val tempImg = File(cacheFolder, "page_${System.currentTimeMillis()}_${i}.jpg")
                                    FileOutputStream(tempImg).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 98, out)
                                    }
                                    bitmap.recycle()
                                    outputPaths.add(tempImg.absolutePath)
                                }
                                renderer.close()
                                pfd.close()
                            } else {
                                outputPaths.add(path)
                            }
                        }
                        outputPaths
                    }
                    success = true
                } catch (_: OutOfMemoryError) {
                    System.gc()
                    currentDpi = when {
                        currentDpi > 600 -> 600
                        currentDpi > 300 -> 300
                        else -> 150
                    }
                } catch (e: Exception) {
                    break
                }
            }

            try {
                if (progressDialog.isShowing) {
                    progressDialog.dismiss()
                }
            } catch (_: Exception) {}

            if (extractedImagePaths.isNotEmpty()) {
                print(context, extractedImagePaths)
            } else {
                Toast.makeText(context, "Failed to render pages", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
