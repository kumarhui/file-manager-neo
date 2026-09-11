package org.fossify.filemanager.customtools.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object DownloadStorageHelper {

    fun saveToDownloads(
        context: Context,
        sourcePath: String,
        fileName: String,
        mimeType: String
    ): Boolean {
        return try {
            val source = File(sourcePath)

            if (!source.exists()) {
                return false
            }

            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                val values = ContentValues().apply {
                    put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        fileName
                    )

                    put(
                        MediaStore.Downloads.MIME_TYPE,
                        mimeType
                    )

                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                    )

                    put(
                        MediaStore.Downloads.IS_PENDING,
                        1
                    )
                }

                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return false

                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        source.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }

                    values.clear()
                    values.put(
                        MediaStore.Downloads.IS_PENDING,
                        0
                    )

                    resolver.update(
                        uri,
                        values,
                        null,
                        null
                    )

                    true
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    false
                }

            } else {

                val downloads = Environment
                    .getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )

                if (!downloads.exists()) {
                    downloads.mkdirs()
                }

                val destination =
                    File(downloads, fileName)

                source.inputStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }

                true
            }

        } catch (e: Exception) {
            false
        }
    }

    fun getPrivateOutputFile(
        context: Context,
        fileName: String
    ): File {

        val directory = File(
            context.cacheDir,
            "customtools"
        )

        if (!directory.exists()) {
            directory.mkdirs()
        }

        return File(
            directory,
            fileName
        )
    }
}
