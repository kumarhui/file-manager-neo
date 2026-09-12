package org.fossify.filemanager.customtools.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.util.Locale

class CustomRenameDialog : DialogFragment() {

    companion object {
        private const val ARG_FILE_PATHS = "file_paths"

        fun show(context: Context, filePaths: List<String>) {
            val activity = context as? FragmentActivity ?: return
            if (activity.isFinishing || activity.isDestroyed || filePaths.isEmpty()) return

            CustomRenameDialog().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_FILE_PATHS, ArrayList(filePaths))
                }
            }.show(activity.supportFragmentManager, "CustomToolsCustomRename")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val paths = arguments?.getStringArrayList(ARG_FILE_PATHS)?.toList() ?: emptyList()
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    CustomRenameContent(
                        filePaths = paths,
                        onDismiss = { dismiss() },
                        onRenamed = { dismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomRenameContent(
    filePaths: List<String>,
    onDismiss: () -> Unit,
    onRenamed: () -> Unit
) {
    val context = LocalContext.current
    val originalNames = remember(filePaths) { filePaths.map { File(it).name } }
    val newNames = remember(filePaths) { mutableStateListOf<String>().apply { addAll(originalNames) } }
    var showPreview by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Custom Rename (Paste List)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Original Names", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { copyNames(context, originalNames) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy all names")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                itemsIndexed(originalNames) { _, name ->
                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("New Names", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val pasted = getClipboardNames(context)
                if (pasted.isNotEmpty()) {
                    newNames.clear()
                    newNames.addAll(filePaths.indices.map { pasted.getOrNull(it) ?: originalNames[it] })
                }
            }) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste names")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            itemsIndexed(newNames) { index, name ->
                OutlinedTextField(
                    value = name,
                    onValueChange = { newNames[index] = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    label = { Text("File ${index + 1}") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(
                onClick = { for (i in newNames.indices) newNames[i] = toTitleCase(newNames[i]) },
                modifier = Modifier.weight(1f)
            ) { Text("Title Case") }

            TextButton(
                onClick = { for (i in newNames.indices) newNames[i] = newNames[i].lowercase(Locale.getDefault()) },
                modifier = Modifier.weight(1f)
            ) { Text("lowercase") }

            TextButton(
                onClick = { for (i in newNames.indices) newNames[i] = newNames[i].uppercase(Locale.getDefault()) },
                modifier = Modifier.weight(1f)
            ) { Text("UPPERCASE") }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { showPreview = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Preview, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Preview & Apply")
        }
    }

    if (showPreview) {
        RenamePreviewDialog(
            originalNames = originalNames,
            newNames = newNames,
            filePaths = filePaths,
            onDismiss = { showPreview = false },
            onRename = {
                val success = renameFiles(filePaths, newNames)
                if (success) {
                    onRenamed()
                } else {
                    Toast.makeText(context, "Some files could not be renamed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

private fun copyNames(context: Context, names: List<String>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("File names", names.joinToString("\n")))
    Toast.makeText(context, "All file names copied", Toast.LENGTH_SHORT).show()
}

private fun getClipboardNames(context: Context): List<String> {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip ?: return emptyList()
    if (clip.itemCount == 0) return emptyList()
    return clip.getItemAt(0).coerceToText(context).toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
}

private fun toTitleCase(value: String): String {
    return value.split(" ").joinToString(" ") { word ->
        if (word.isEmpty()) word
        else word.substring(0, 1).uppercase(Locale.getDefault()) + word.substring(1).lowercase(Locale.getDefault())
    }
}

@Composable
private fun RenamePreviewDialog(
    originalNames: List<String>,
    newNames: List<String>,
    filePaths: List<String>,
    onDismiss: () -> Unit,
    onRename: () -> Unit
) {
    val duplicateNames = newNames.groupingBy { it.lowercase(Locale.getDefault()) }.eachCount().filter { it.value > 1 }.keys
    val existingNames = remember(newNames, filePaths) {
        newNames.mapIndexedNotNull { index, name ->
            val parent = File(filePaths[index]).parentFile ?: return@mapIndexedNotNull null
            val target = File(parent, name)
            if (!target.absolutePath.equals(File(filePaths[index]).absolutePath, ignoreCase = true) && target.exists()) name else null
        }.toSet()
    }

    val hasErrors = newNames.any { it.isBlank() } || duplicateNames.isNotEmpty() || existingNames.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Preview") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                itemsIndexed(originalNames) { index, original ->
                    val newName = newNames.getOrNull(index) ?: ""
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(original, style = MaterialTheme.typography.bodySmall)
                        Text("→ $newName", style = MaterialTheme.typography.bodyMedium)
                        if (newName.lowercase(Locale.getDefault()) in duplicateNames) {
                            Text("Duplicate name", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                        if (newName in existingNames) {
                            Text("File already exists", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onRename, enabled = !hasErrors) {
                Text("Rename ${newNames.size} Files")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun renameFiles(filePaths: List<String>, newNames: List<String>): Boolean {
    if (filePaths.size != newNames.size || newNames.any { it.isBlank() }) return false

    val targets = filePaths.mapIndexed { index, path -> File(File(path).parentFile, newNames[index]) }
    if (targets.map { it.absolutePath.lowercase() }.distinct().size != targets.size) return false

    for (index in filePaths.indices) {
        val source = File(filePaths[index])
        val target = targets[index]
        if (target.exists() && !source.absolutePath.equals(target.absolutePath, ignoreCase = true)) return false
    }

    val renamed = mutableListOf<Pair<File, File>>()
    try {
        for (index in filePaths.indices) {
            val source = File(filePaths[index])
            val target = targets[index]
            if (source.absolutePath.equals(target.absolutePath, ignoreCase = true)) continue
            if (!source.renameTo(target)) {
                for ((oldFile, newFile) in renamed.asReversed()) newFile.renameTo(oldFile)
                return false
            }
            renamed.add(source to target)
        }
        return true
    } catch (_: Exception) {
        for ((oldFile, newFile) in renamed.asReversed()) newFile.renameTo(oldFile)
        return false
    }
}
