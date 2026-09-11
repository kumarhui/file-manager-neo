package org.fossify.filemanager.customtools.info

import android.graphics.BitmapFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ResultInfoHelper {

    data class ImageInfo(
        val name: String,
        val path: String,
        val size: Long,
        val width: Int,
        val height: Int,
        val modified: String
    )

    fun getInfo(
        path: String
    ): ImageInfo? {

        val file = File(path)

        if (!file.exists()) {
            return null
        }

        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        BitmapFactory.decodeFile(
            path,
            options
        )

        return ImageInfo(
            name = file.name,
            path = file.absolutePath,
            size = file.length(),
            width = options.outWidth,
            height = options.outHeight,
            modified =
                SimpleDateFormat(
                    "dd MMM yyyy, HH:mm",
                    Locale.getDefault()
                ).format(
                    Date(file.lastModified())
                )
        )
    }
}
