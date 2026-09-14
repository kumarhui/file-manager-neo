package org.fossify.filemanager.customtools.idcard

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

const val ID_CARD_ASPECT_RATIO = 85.60f / 53.98f

data class PagePair(
    val left: Bitmap?,
    val right: Bitmap?
)

class IdCardActivity : ComponentActivity() {

    companion object {
        const val EXTRA_IMAGE_PATHS = "extra_image_paths"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val paths = intent.getStringArrayListExtra(EXTRA_IMAGE_PATHS) ?: arrayListOf()
        val uris = paths.map { Uri.fromFile(File(it)) }

        setContent {
            MaterialTheme {
                IdCardPipelineScreen(
                    uris = uris,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardPipelineScreen(
    uris: List<Uri>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pageSlots by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(true) }
    var progressStatus by remember { mutableStateOf("Initializing scanner engine...") }

    var previewPair by remember { mutableStateOf<PagePair?>(null) }
    var previewSheetBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingPreview by remember { mutableStateOf(false) }

    LaunchedEffect(uris) {
        withContext(Dispatchers.IO) {
            val processed = mutableListOf<Bitmap>()
            uris.forEachIndexed { index, uri ->
                progressStatus = "Scanning document ${index + 1} of ${uris.size}..."
                try {
                    val bmp = FairScanIdPipeline.processDocument(context, uri)
                    processed.add(bmp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            pageSlots = processed
        }
        isProcessing = false
    }

    val pairs = remember(pageSlots) {
        val list = mutableListOf<PagePair>()
        var i = 0
        while (i < pageSlots.size) {
            val left = pageSlots.getOrNull(i)
            val right = pageSlots.getOrNull(i + 1)
            list.add(PagePair(left, right))
            i += 2
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ID Card Pair Pipeline", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val firstPair = pairs.firstOrNull { it.left != null || it.right != null }
                                if (firstPair != null) {
                                    val a4 = FairScanIdPipeline.generateA4CompositeSheet(context, firstPair.left, firstPair.right)
                                    sendToNokoPrint(context, a4)
                                }
                            }
                        },
                        enabled = pairs.isNotEmpty() && !isProcessing
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print first pair to NokoPrint", tint = Color(0xFF0284C7))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.White
    ) { innerPadding ->

        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (pairs.isEmpty() && !isProcessing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No documents loaded", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(pairs) { pairIndex, pair ->
                        val leftSlotIndex = pairIndex * 2
                        val rightSlotIndex = pairIndex * 2 + 1

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left Card Slot
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(ID_CARD_ASPECT_RATIO)
                                    ) {
                                        if (pair.left != null) {
                                            CardItem(
                                                bitmap = pair.left,
                                                label = "Left (${leftSlotIndex + 1})",
                                                onClick = {
                                                    // Isolate / Revert logic exactly like DocuLab
                                                    val mutable = pageSlots.toMutableList()
                                                    if (pair.right == null && rightSlotIndex < mutable.size) {
                                                        // Revert: Pull next item into right slot
                                                        mutable.removeAt(rightSlotIndex)
                                                    } else if (pair.right != null) {
                                                        // Isolate: Push right card down
                                                        mutable.add(rightSlotIndex, null)
                                                    }
                                                    pageSlots = mutable
                                                }
                                            )
                                        } else {
                                            BlankSlotCard(label = "Empty (Click to close)") {
                                                val mutable = pageSlots.toMutableList()
                                                if (leftSlotIndex < mutable.size && mutable[leftSlotIndex] == null) {
                                                    mutable.removeAt(leftSlotIndex)
                                                    pageSlots = mutable
                                                }
                                            }
                                        }
                                    }

                                    // Action Column (Swap & A4 Preview)
                                    Column(
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val mutable = pageSlots.toMutableList()
                                                while (mutable.size <= rightSlotIndex) mutable.add(null)
                                                val tmp = mutable[leftSlotIndex]
                                                mutable[leftSlotIndex] = mutable[rightSlotIndex]
                                                mutable[rightSlotIndex] = tmp
                                                pageSlots = mutable
                                            },
                                            modifier = Modifier.size(36.dp).background(Color.White, CircleShape).border(1.dp, Color(0xFFE2E8F0), CircleShape)
                                        ) {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = "Swap Pair", tint = Color(0xFF0284C7))
                                        }

                                        IconButton(
                                            onClick = {
                                                previewPair = pair
                                                isGeneratingPreview = true
                                                scope.launch {
                                                    previewSheetBitmap = FairScanIdPipeline.generateA4CompositeSheet(context, pair.left, pair.right)
                                                    isGeneratingPreview = false
                                                }
                                            },
                                            modifier = Modifier.size(36.dp).background(Color.White, CircleShape).border(1.dp, Color(0xFFE2E8F0), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = "Preview on A4 Sheet", tint = Color(0xFF475569), modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    // Right Card Slot
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(ID_CARD_ASPECT_RATIO)
                                    ) {
                                        if (pair.right != null) {
                                            CardItem(
                                                bitmap = pair.right,
                                                label = "Right (${rightSlotIndex + 1})",
                                                onClick = {
                                                    // Isolate / Revert logic
                                                    val mutable = pageSlots.toMutableList()
                                                    if (pair.left == null && leftSlotIndex < mutable.size) {
                                                        // Revert: Remove gap before it
                                                        mutable.removeAt(leftSlotIndex)
                                                    } else if (pair.left != null) {
                                                        // Isolate: Push self into new row
                                                        mutable.add(leftSlotIndex, null)
                                                    }
                                                    pageSlots = mutable
                                                }
                                            )
                                        } else {
                                            BlankSlotCard(label = "Empty (Click to close)") {
                                                val mutable = pageSlots.toMutableList()
                                                if (rightSlotIndex < mutable.size && mutable[rightSlotIndex] == null) {
                                                    mutable.removeAt(rightSlotIndex)
                                                    pageSlots = mutable
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // General Processing Dialog
            if (isProcessing) {
                Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, tonalElevation = 6.dp) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(progressStatus, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                        }
                    }
                }
            }

            // A4 Preview Modal Dialog for the tapped pair
            previewPair?.let {
                Dialog(
                    onDismissRequest = { previewPair = null; previewSheetBitmap = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TopAppBar(
                                title = { Text("A4 Layout Preview", fontWeight = FontWeight.Bold) },
                                navigationIcon = {
                                    IconButton(onClick = { previewPair = null; previewSheetBitmap = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                },
                                actions = {
                                    Button(
                                        onClick = {
                                            previewSheetBitmap?.let { bmp -> sendToNokoPrint(context, bmp) }
                                        },
                                        enabled = previewSheetBitmap != null && !isGeneratingPreview,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Print Sheet")
                                    }
                                }
                            )

                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isGeneratingPreview) {
                                    CircularProgressIndicator()
                                } else if (previewSheetBitmap != null) {
                                    Card(
                                        shape = RoundedCornerShape(4.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                        modifier = Modifier.fillMaxHeight().aspectRatio(1f / 1.4142f)
                                    ) {
                                        Image(
                                            bitmap = previewSheetBitmap!!.asImageBitmap(),
                                            contentDescription = "A4 Preview Sheet",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().background(Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardItem(
    bitmap: Bitmap,
    label: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxSize().clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(Color.White)
            )
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(bottomEnd = 6.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun BlankSlotCard(
    label: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
        border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxSize().clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.NoteAdd, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
        }
    }
}

private fun sendToNokoPrint(context: android.content.Context, sheet: Bitmap) {
    try {
        val cacheFolder = File(context.cacheDir, "id_print_sheets").apply { mkdirs() }
        val tempFile = File(cacheFolder, "ID_A4_${System.currentTimeMillis()}.png")
        FileOutputStream(tempFile).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        intent.setPackage("com.nokoprint")
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            intent.setPackage("com.noco.print")
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not launch NokoPrint: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}