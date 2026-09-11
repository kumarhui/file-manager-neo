package org.fossify.filemanager.customtools.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object NokoPrintHelper {

    fun print(
        context: Context,
        imagePath: String
    ): Boolean {
        if (imagePath.isBlank()) {
            return false
        }

        val file = File(imagePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
            return false
        }

        val uri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } catch (_: Exception) {
            Uri.fromFile(file)
        }

        val printIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.noco.print")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Launches NokoPrint into its own card in the Recents list
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }

        return try {
            context.startActivity(printIntent)
            true
        } catch (_: Exception) {
            printIntent.setPackage("com.nokoprint")
            try {
                context.startActivity(printIntent)
                true
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    "NokoPrint is not installed",
                    Toast.LENGTH_SHORT
                ).show()
                false
            }
        }
    }
}
