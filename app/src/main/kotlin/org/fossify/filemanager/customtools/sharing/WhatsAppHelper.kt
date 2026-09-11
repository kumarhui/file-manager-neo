package org.fossify.filemanager.customtools.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object WhatsAppHelper {

    fun share(context: Context, imagePath: String): Boolean {
        return share(context, listOf(imagePath))
    }

    fun share(context: Context, imagePaths: List<String>): Boolean {
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

            intent.setPackage("com.whatsapp")
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            try {
                // Fallback generic share if WhatsApp package check fails
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    val uris = imagePaths.map { FileProvider.getUriForFile(context, "${context.packageName}.provider", File(it)) }.let { ArrayList(it) }
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share via"))
                return true
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                return false
            }
        }
    }
}