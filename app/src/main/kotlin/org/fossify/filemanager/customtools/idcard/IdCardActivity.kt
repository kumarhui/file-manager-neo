package org.fossify.filemanager.customtools.idcard

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.filemanager.R
import org.fossify.filemanager.customtools.layout.IdCardLayoutEngine
import org.fossify.filemanager.customtools.passport.PassportPhotoProcessor
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IdCardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val imagePaths = intent.getStringArrayListExtra(EXTRA_IMAGE_PATHS) ?: arrayListOf()
        if (imagePaths.isEmpty()) {
            Toast.makeText(this, "No image provided for ID Card", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                IdCardScreen(
                    initialPaths = imagePaths,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_IMAGE_PATHS = "extra_image_paths"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardScreen(
    initialPaths: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imagePaths by remember { mutableStateOf(initialPaths) }
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var isGenerating by remember { mutableStateOf(false) }
    var isVerticalLayout by remember { mutableStateOf(true) }

    LaunchedEffect(imagePaths) {
        val loaded = imagePaths.mapNotNull { path ->
            PassportPhotoProcessor.loadBitmap(context, Uri.fromFile(File(path)))
        }
        bitmaps = loaded
    }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val croppedUri = UCrop.getOutput(res.data!!)
            croppedUri?.let { uri ->
                scope.launch {
                    val updated = PassportPhotoProcessor.loadBitmap(context, uri)
                    if (updated != null && selectedIndex in bitmaps.indices) {
                        val list = bitmaps.toMutableList()
                        list[selectedIndex] = updated
                        bitmaps = list
                    }
                }
            }
        }
    }

    val addImagesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val newPaths = mutableListOf<String>()
                uris.forEach { uri ->
                    try {
                        val stream = context.contentResolver.openInputStream(uri)
                        val cacheFile = File(
                            context.cacheDir,
                            "imported_id_${System.currentTimeMillis()}_${(0..999).random()}.jpg"
                        )
                        FileOutputStream(cacheFile).use { out -> stream?.copyTo(out) }
                        newPaths.add(cacheFile.absolutePath)
                    } catch (_: Exception) {}
                }
                if (newPaths.isNotEmpty()) {
                    imagePaths = imagePaths + newPaths
                }
            }
        }
    }

    LaunchedEffect(bitmaps, isVerticalLayout) {
        if (bitmaps.isEmpty()) return@LaunchedEffect
        isGenerating = true
        withContext(Dispatchers.Default) {
            pages = IdCardLayoutEngine.createMultiPageSheets(bitmaps, isVerticalLayout)
            if (currentPageIndex >= pages.size) {
                currentPageIndex = (pages.size - 1).coerceAtLeast(0)
            }
        }
        isGenerating = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("ID Card on A4", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Loaded Images (${bitmaps.size}) - 2 per A4 sheet",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            // Carousel with Add Button
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(bitmaps) { index, bmp ->
                    val isSelected = index == selectedIndex
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(75.dp, 50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedIndex = index },
                        contentScale = ContentScale.Crop
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .size(75.dp, 50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .clickable { addImagesPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Images",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    if (selectedIndex in imagePaths.indices) {
                        val src = Uri.fromFile(File(imagePaths[selectedIndex]))
                        val dest = Uri.fromFile(File(context.cacheDir, "id_crop_${System.currentTimeMillis()}.png"))

                        val options = UCrop.Options().apply {
                            withAspectRatio(85.6f, 53.98f)
                            setHideBottomControls(false)
                            setFreeStyleCropEnabled(false)
                            setCompressionQuality(90)
                            // Fix status bar overlap coloring
                            setStatusBarColor(android.graphics.Color.parseColor("#1F1F1F"))
                            setToolbarColor(android.graphics.Color.parseColor("#1F1F1F"))
                            setToolbarWidgetColor(android.graphics.Color.WHITE)
                        }

                        val intent = UCrop.of(src, dest)
                            .withOptions(options)
                            .getIntent(context)

                        cropLauncher.launch(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Crop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Crop Selected (ID-1 Aspect Ratio)")
            }

            // Sheet Preview & Page Navigation
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Page ${if (pages.isEmpty()) 0 else currentPageIndex + 1} of ${pages.size}",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isVerticalLayout) "Layout: Top-Bottom" else "Layout: Left-Right",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Layout Switch Icon (Toggles Vertical / Horizontal)
                            IconButton(
                                onClick = {
                                    isVerticalLayout = !isVerticalLayout
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_tool_a4),
                                    contentDescription = "Switch Layout Style",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Swap Individual Card Icon (Interchanges selected image with its pair)
                            IconButton(
                                onClick = {
                                    if (bitmaps.size >= 2 && selectedIndex >= 0) {
                                        val mutableBitmaps = bitmaps.toMutableList()
                                        val targetIndex = if (selectedIndex % 2 == 0) {
                                            (selectedIndex + 1).coerceAtMost(mutableBitmaps.size - 1)
                                        } else {
                                            (selectedIndex - 1).coerceAtLeast(0)
                                        }
                                        if (targetIndex != selectedIndex) {
                                            val temp = mutableBitmaps[selectedIndex]
                                            mutableBitmaps[selectedIndex] = mutableBitmaps[targetIndex]
                                            mutableBitmaps[targetIndex] = temp
                                            bitmaps = mutableBitmaps
                                            selectedIndex = targetIndex
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_tool_crop),
                                    contentDescription = "Swap Individual Card Position",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (pages.size > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                        enabled = currentPageIndex > 0,
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Prev", fontSize = 11.sp)
                                    }
                                    FilledTonalButton(
                                        onClick = { if (currentPageIndex < pages.size - 1) currentPageIndex++ },
                                        enabled = currentPageIndex < pages.size - 1,
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Next", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(390.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator()
                        } else {
                            pages.getOrNull(currentPageIndex)?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "A4 ID Card Sheet",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = {
                            pages.getOrNull(currentPageIndex)?.let { bmp ->
                                scope.launch {
                                    val uri = saveIdCardPageToDownloads(context, bmp, currentPageIndex + 1)
                                    Toast.makeText(
                                        context,
                                        if (uri != null) "Saved Page ${currentPageIndex + 1} to Downloads" else "Failed to save",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Download Current Page")
                        }

                        IconButton(onClick = {
                            pages.getOrNull(currentPageIndex)?.let { bmp ->
                                scope.launch {
                                    val file = saveIdCardTempFile(context, bmp)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        setPackage("com.noco.print")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        intent.setPackage("com.nokoprint")
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "NokoPrint not installed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Print, contentDescription = "Print Current Page")
                        }

                        IconButton(onClick = {
                            pages.getOrNull(currentPageIndex)?.let { bmp ->
                                scope.launch {
                                    val file = saveIdCardTempFile(context, bmp)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        setPackage("com.whatsapp")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share via WhatsApp")
                        }
                    }
                }
            }
        }
    }
}

suspend fun saveIdCardPageToDownloads(context: Context, bitmap: Bitmap, pageNumber: Int): Uri? = withContext(Dispatchers.IO) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "ID_Card_A4_Page${pageNumber}_$timestamp.jpg"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = context.contentResolver.insert(collection, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
        uri
    } catch (_: Exception) {
        null
    }
}

suspend fun saveIdCardTempFile(context: Context, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, "id_card_page_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    file
}
