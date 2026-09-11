package org.fossify.filemanager.customtools.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import org.fossify.filemanager.customtools.conversion.PdfPageExtractor
import org.fossify.filemanager.customtools.preview.ResultDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfPageExtractionDialog : DialogFragment() {

    companion object {
        private const val TAG = "PdfPageExtractionDialog"
        private const val ARG_PATHS = "pdf_paths"

        fun show(context: Context, paths: List<String>) {
            val activity = context as? FragmentActivity ?: return
            val manager = activity.supportFragmentManager
            if (activity.isFinishing || activity.isDestroyed || manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return

            PdfPageExtractionDialog().apply {
                arguments = Bundle().apply { putStringArrayList(ARG_PATHS, ArrayList(paths)) }
            }.show(manager, TAG)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val paths = arguments?.getStringArrayList(ARG_PATHS)?.toList().orEmpty()
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    ExtractionContent(
                        count = paths.size,
                        onCancel = { dismiss() },
                        onExtract = { destination ->
                            val ctx = requireContext()
                            dismiss()
                            CoroutineScope(Dispatchers.IO).launch {
                                val outputs = try {
                                    PdfPageExtractor.extract(paths, destination)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                                withContext(Dispatchers.Main) {
                                    if (outputs.isNotEmpty()) {
                                        ResultDialog.showMultiple(ctx, outputs, "Extracted ${outputs.size} PDF Pages")
                                    } else {
                                        android.widget.Toast.makeText(ctx, "PDF page extraction failed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtractionContent(
    count: Int,
    onCancel: () -> Unit,
    onExtract: (PdfPageExtractor.Destination) -> Unit
) {
    var selected by remember { mutableStateOf(PdfPageExtractor.Destination.SAME_FOLDER) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Convert PDF to Images") },
        text = {
            Column {
                Text(
                    if (count == 1) "Extract every page of this PDF as an image." else "Extract every page of $count PDFs as images.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = { selected = PdfPageExtractor.Destination.SAME_FOLDER }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == PdfPageExtractor.Destination.SAME_FOLDER,
                        onClick = {
                            selected = PdfPageExtractor.Destination.SAME_FOLDER
                        }
                    )
                    Text("Same folder")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = { selected = PdfPageExtractor.Destination.NEW_FOLDER }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == PdfPageExtractor.Destination.NEW_FOLDER,
                        onClick = {
                            selected = PdfPageExtractor.Destination.NEW_FOLDER
                        }
                    )
                    Text("New folder")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onExtract(selected) }) { Text("Extract") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
