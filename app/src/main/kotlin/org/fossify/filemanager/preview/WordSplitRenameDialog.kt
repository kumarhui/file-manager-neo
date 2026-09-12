package org.fossify.filemanager.customtools.preview

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import java.io.File

class WordSplitRenameDialog : DialogFragment() {

    companion object {
        private const val ARG_FILE_PATHS = "file_paths"

        fun show(context: Context, filePaths: List<String>) {
            val activity = context as? FragmentActivity ?: return
            if (activity.isFinishing || activity.isDestroyed || filePaths.isEmpty()) return

            WordSplitRenameDialog().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_FILE_PATHS, ArrayList(filePaths))
                }
            }.show(activity.supportFragmentManager, "WordSplitRenameDialog")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val paths = arguments?.getStringArrayList(ARG_FILE_PATHS)?.toList() ?: emptyList()
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    WordSplitContent(
                        filePaths = paths,
                        onDismiss = { dismiss() },
                        onDone = { dismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun WordSplitContent(
    filePaths: List<String>,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var separator by remember { mutableStateOf(" ") }
    var isBatchMode by remember { mutableStateOf(false) }

    val fileTokensList = remember(filePaths) {
        filePaths.map { path ->
            val base = File(path).nameWithoutExtension
            val tokens = base.split(Regex("[\\s_\\-]+")).filter { it.isNotBlank() }
            mutableStateListOf<String>().apply { addAll(if (tokens.isEmpty()) listOf(base) else tokens) }
        }
    }

    val currentFile = filePaths.getOrNull(currentIndex) ?: ""
    val currentTokens = fileTokensList.getOrNull(currentIndex) ?: remember { mutableStateListOf() }

    val currentNewName: (Int) -> String = { idx ->
        val file = File(filePaths[idx])
        val tokens = if (isBatchMode && idx != currentIndex) {
            val originalBaseTokens = file.nameWithoutExtension.split(Regex("[\\s_\\-]+")).filter { it.isNotBlank() }
            originalBaseTokens.mapIndexed { tIdx, t -> currentTokens.getOrNull(tIdx) ?: t }
        } else {
            fileTokensList[idx]
        }
        val body = tokens.filter { it.isNotBlank() }.joinToString(separator)
        val p = if (prefix.isNotBlank()) "${prefix.trim()} " else ""
        val s = if (suffix.isNotBlank()) " ${suffix.trim()}" else ""
        "$p$body$s.${file.extension}".trim()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Word Split Rename", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        // Mode switch: Individual vs Batch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !isBatchMode,
                onClick = { isBatchMode = false },
                label = { Text("Individual") }
            )
            FilterChip(
                selected = isBatchMode,
                onClick = { isBatchMode = true },
                label = { Text("Batch Template") }
            )
        }

        // Navigator card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentIndex > 0) currentIndex-- },
                    enabled = currentIndex > 0
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev") }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("File ${currentIndex + 1} of ${filePaths.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(File(currentFile).name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }

                IconButton(
                    onClick = { if (currentIndex < filePaths.size - 1) currentIndex++ },
                    enabled = currentIndex < filePaths.size - 1
                ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next") }
            }
        }

        // Prefix, Separator, Suffix
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = { Text("Prefix") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = separator,
                onValueChange = { separator = it },
                label = { Text("Sep") },
                singleLine = true,
                modifier = Modifier.width(60.dp)
            )
            OutlinedTextField(
                value = suffix,
                onValueChange = { suffix = it },
                label = { Text("Suffix") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Text("Token Words:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

        // Horizontal list of word chips/textfields
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(currentTokens) { tokenIdx, tokenVal ->
                OutlinedTextField(
                    value = tokenVal,
                    onValueChange = { currentTokens[tokenIdx] = it },
                    label = { Text("W${tokenIdx + 1}") },
                    singleLine = true,
                    modifier = Modifier.width(110.dp)
                )
            }
        }

        Text("Preview Changes:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                itemsIndexed(filePaths) { idx, path ->
                    val old = File(path).name
                    val newN = currentNewName(idx)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (idx == currentIndex) Color(0xFFE0F2FE) else Color.Transparent)
                            .padding(4.dp)
                    ) {
                        Text(old, fontSize = 11.sp, color = Color.Gray)
                        Text("→ $newN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (old != newN) Color(0xFF0284C7) else Color.DarkGray)
                    }
                }
            }
        }

        Button(
            onClick = {
                val targets = filePaths.indices.map { idx -> currentNewName(idx) }
                val ok = renameBulk(filePaths, targets)
                if (ok) {
                    onDone()
                } else {
                    Toast.makeText(onDismiss as? Context, "Some files could not be renamed", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply Rename")
        }
    }
}

private fun renameBulk(filePaths: List<String>, newNames: List<String>): Boolean {
    val renamed = mutableListOf<Pair<File, File>>()
    try {
        for (i in filePaths.indices) {
            val src = File(filePaths[i])
            val tgt = File(src.parentFile, newNames[i])
            if (src.absolutePath.equals(tgt.absolutePath, ignoreCase = true)) continue
            if (!src.renameTo(tgt)) {
                for ((o, n) in renamed.asReversed()) n.renameTo(o)
                return false
            }
            renamed.add(src to tgt)
        }
        return true
    } catch (_: Exception) {
        for ((o, n) in renamed.asReversed()) n.renameTo(o)
        return false
    }
}
