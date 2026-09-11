package org.fossify.filemanager.customtools.preview

import android.content.Context
import android.graphics.Bitmap
import org.fossify.filemanager.customtools.storage.DownloadStorageHelper
import java.io.FileOutputStream

object A4PreviewDialog {

    fun show(
        context: Context,
        bitmap: Bitmap,
        title: String = "A4 Preview"
    ) {

        val file =
            DownloadStorageHelper.getPrivateOutputFile(
                context,
                "a4_${System.currentTimeMillis()}.png"
            )

        FileOutputStream(file).use {
            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                it
            )
        }

        ResultDialog.show(
            context,
            file.absolutePath,
            title
        )
    }
}
