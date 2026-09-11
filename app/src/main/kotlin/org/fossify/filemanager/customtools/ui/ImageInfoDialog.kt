package org.fossify.filemanager.customtools.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageInfoDialog : DialogFragment() {

    private var imagePath = ""

    companion object {

        private const val ARG_IMAGE_PATH = "image_path"
        private const val TAG = "ImageInfoDialog"

        fun show(
            context: Context,
            imagePath: String
        ) {

            val activity =
                context as? FragmentActivity
                    ?: return

            if (
                activity.isFinishing ||
                activity.isDestroyed
            ) {
                return
            }

            val manager =
                activity.supportFragmentManager

            if (
                manager.isStateSaved ||
                manager.findFragmentByTag(TAG) != null
            ) {
                return
            }

            ImageInfoDialog().apply {

                arguments = Bundle().apply {
                    putString(
                        ARG_IMAGE_PATH,
                        imagePath
                    )
                }

            }.show(
                manager,
                TAG
            )
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        imagePath =
            arguments
                ?.getString(ARG_IMAGE_PATH)
                .orEmpty()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val file = File(imagePath)

        val info = ImageInfo(
            name = file.name,
            path = file.absolutePath,
            size = formatSize(file.length()),
            modified = formatDate(file.lastModified())
        )

        return ComposeView(requireContext()).apply {

            setContent {

                MaterialTheme {

                    ImageInfoContent(
                        info = info,
                        onDismiss = {
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {

        super.onStart()

        dialog?.window?.let { window ->

            window.setBackgroundDrawableResource(
                android.R.color.transparent
            )

            window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            window.setDimAmount(0.55f)

            window.setGravity(
                Gravity.CENTER
            )

            window.setLayout(
                (resources.displayMetrics.widthPixels * 0.90f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun formatSize(
        bytes: Long
    ): String {

        if (bytes <= 0) {
            return "0 B"
        }

        val units =
            arrayOf(
                "B",
                "KB",
                "MB",
                "GB"
            )

        var value =
            bytes.toDouble()

        var index = 0

        while (
            value >= 1024 &&
            index < units.lastIndex
        ) {

            value /= 1024
            index++
        }

        return if (index == 0) {
            "${value.toLong()} ${units[index]}"
        } else {
            String.format(
                Locale.getDefault(),
                "%.2f %s",
                value,
                units[index]
            )
        }
    }

    private fun formatDate(
        timestamp: Long
    ): String {

        if (timestamp <= 0) {
            return "Unknown"
        }

        return SimpleDateFormat(
            "dd MMM yyyy, HH:mm",
            Locale.getDefault()
        ).format(
            Date(timestamp)
        )
    }
}

private data class ImageInfo(
    val name: String,
    val path: String,
    val size: String,
    val modified: String
)

@Composable
private fun ImageInfoContent(
    info: ImageInfo,
    onDismiss: () -> Unit
) {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(24.dp),

        tonalElevation = 8.dp,

        shadowElevation = 12.dp
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),

            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Text(
                text = "Image Information",

                style =
                    MaterialTheme
                        .typography
                        .headlineSmall
            )

            Spacer(
                modifier =
                    Modifier.size(4.dp)
            )

            InfoRow(
                label = "Name",
                value = info.name
            )

            InfoRow(
                label = "Size",
                value = info.size
            )

            InfoRow(
                label = "Modified",
                value = info.modified
            )

            InfoRow(
                label = "Path",
                value = info.path
            )

            Spacer(
                modifier =
                    Modifier.size(4.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.End
            ) {

                TextButton(
                    onClick = onDismiss
                ) {

                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {

    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Text(
            text = label,

            style =
                MaterialTheme
                    .typography
                    .labelMedium,

            color =
                MaterialTheme
                    .colorScheme
                    .primary
        )

        Text(
            text = value,

            style =
                MaterialTheme
                    .typography
                    .bodyMedium
        )
    }
}
