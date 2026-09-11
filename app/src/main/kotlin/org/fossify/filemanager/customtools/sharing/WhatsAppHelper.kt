package org.fossify.filemanager.customtools.sharing

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object WhatsAppHelper {

    fun share(
        context: Context,
        path: String
    ): Boolean {

        val file = File(path)

        if (!file.exists()) {
            return false
        }

        return try {

            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

            val intent =
                Intent(Intent.ACTION_SEND).apply {

                    type = "image/*"

                    putExtra(
                        Intent.EXTRA_STREAM,
                        uri
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    setPackage(
                        "com.whatsapp"
                    )
                }

            context.startActivity(intent)

            true

        } catch (e: Exception) {
            false
        }
    }
}
