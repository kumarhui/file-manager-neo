package org.fossify.filemanager.customtools.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object JuganuaHelper {

    private const val JUGANUA_PACKAGE = "cvam.dignity.juganua"

    fun open(
        context: Context,
        imagePath: String
    ): Boolean {

        val file = File(imagePath)

        if (!file.exists()) {
            return false
        }

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

                setPackage(
                    JUGANUA_PACKAGE
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openMultiple(
        context: Context,
        imagePaths: List<String>
    ): Boolean {

        val uris =
            imagePaths
                .map { File(it) }
                .filter { it.exists() }
                .map {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        it
                    )
                }

        if (uris.isEmpty()) {
            return false
        }

        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {

                type = "image/*"

                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList<Uri>(uris)
                )

                setPackage(
                    JUGANUA_PACKAGE
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
