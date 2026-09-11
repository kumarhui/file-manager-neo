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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.filemanager.customtools.conversion.ImageConverter
import org.fossify.filemanager.customtools.preview.ResultDialog
import java.io.File

class ImageCompressionDialog : DialogFragment() {

    private var imagePath =
        ""

    companion object {

        private const val ARG_IMAGE_PATH =
            "image_path"

        private const val TAG =
            "ImageCompressionDialog"

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

            ImageCompressionDialog()
                .apply {

                    arguments =
                        Bundle().apply {
                            putString(
                                ARG_IMAGE_PATH,
                                imagePath
                            )
                        }
                }
                .show(
                    manager,
                    TAG
                )
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

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

        return ComposeView(
            requireContext()
        ).apply {

            setContent {

                MaterialTheme {

                    CompressionContent(
                        imagePath = imagePath,

                        onDismiss = {
                            dismiss()
                        },

                        onCompress = { quality ->
                            compressImage(
                                quality
                            )
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

            window.setDimAmount(
                0.55f
            )

            window.setGravity(
                Gravity.CENTER
            )

            window.setLayout(
                (
                    resources.displayMetrics
                        .widthPixels * 0.92f
                ).toInt(),

                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun compressImage(
        quality: Int
    ) {

        if (imagePath.isBlank()) {
            return
        }

        val result =
            ImageConverter.compress(
                context = requireContext(),
                inputPath = imagePath,
                quality = quality
            )

        if (
            result.success &&
            result.outputPath != null
        ) {

            dismiss()

            ResultDialog.show(
                requireContext(),
                result.outputPath,
                "Compressed Image"
            )

        } else {

            android.widget.Toast.makeText(
                requireContext(),
                result.error
                    ?: "Compression failed",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@Composable
private fun CompressionContent(
    imagePath: String,
    onDismiss: () -> Unit,
    onCompress: (Int) -> Unit
) {

    var quality by remember {
        mutableIntStateOf(75)
    }

    var estimatedSize by remember {
        mutableStateOf<Long?>(null)
    }

    var calculating by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(
        imagePath,
        quality
    ) {

        calculating = true

        estimatedSize =
            withContext(
                Dispatchers.Default
            ) {

                ImageConverter
                    .estimateCompressedSize(
                        imagePath,
                        quality
                    )
            }

        calculating = false
    }

    val originalSize =
        remember(imagePath) {

            File(imagePath)
                .takeIf { it.exists() }
                ?.length()
        }

    Surface(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            androidx.compose.foundation
                .shape
                .RoundedCornerShape(
                    24.dp
                ),

        tonalElevation = 8.dp,

        shadowElevation = 12.dp
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
        ) {

            Text(
                text = "Compress Image",

                style =
                    MaterialTheme
                        .typography
                        .headlineSmall
            )

            Spacer(
                modifier =
                    Modifier.size(6.dp)
            )

            Text(
                text =
                    "Choose how much to compress the image.",

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,

                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                modifier =
                    Modifier.size(18.dp)
            )

            Text(
                text = "$quality% quality",

                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            Slider(
                value =
                    quality.toFloat(),

                onValueChange = {
                    quality =
                        it.toInt()
                            .coerceIn(
                                10,
                                100
                            )
                },

                valueRange =
                    10f..100f,

                steps = 17,

                modifier =
                    Modifier.fillMaxWidth()
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    "10%",
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )

                Text(
                    "Smaller",
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,

                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )

                Text(
                    "100%",
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }

            Spacer(
                modifier =
                    Modifier.size(18.dp)
            )

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    androidx.compose.foundation
                        .shape
                        .RoundedCornerShape(
                            16.dp
                        ),

                color =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
            ) {

                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        )
                ) {

                    Text(
                        text =
                            "Size",

                        style =
                            MaterialTheme
                                .typography
                                .titleSmall
                    )

                    Spacer(
                        modifier =
                            Modifier.size(6.dp)
                    )

                    Text(
                        text =
                            "Original: ${
                                formatFileSize(
                                    originalSize
                                )
                            }",

                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )

                    Text(
                        text =
                            if (calculating) {

                                "After compression: calculating…"

                            } else {

                                "After compression: ${
                                    formatFileSize(
                                        estimatedSize
                                    )
                                }"
                            },

                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )

                    if (
                        originalSize != null &&
                        estimatedSize != null
                    ) {

                        val reduction =
                            (
                                100f -
                                    (
                                        estimatedSize!!
                                            .toFloat() /
                                            originalSize
                                                .toFloat() *
                                            100f
                                    )
                            )
                                .coerceIn(
                                    0f,
                                    100f
                                )

                        Spacer(
                            modifier =
                                Modifier.size(4.dp)
                        )

                        Text(
                            text =
                                "${reduction.toInt()}% smaller",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.size(18.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.End,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                TextButton(
                    onClick =
                        onDismiss
                ) {

                    Text(
                        "Cancel"
                    )
                }

                Button(
                    onClick = {
                        onCompress(
                            quality
                        )
                    }
                ) {

                    Text(
                        "Compress"
                    )
                }
            }
        }
    }
}

private fun formatFileSize(
    size: Long?
): String {

    if (
        size == null ||
        size < 0
    ) {
        return "—"
    }

    return when {

        size < 1024 ->
            "$size B"

        size < 1024 * 1024 ->
            "%.1f KB".format(
                size / 1024f
            )

        else ->
            "%.2f MB".format(
                size /
                    (1024f * 1024f)
            )
    }
}
