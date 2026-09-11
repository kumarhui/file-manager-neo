package org.fossify.filemanager.customtools.passport

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class PassportA4SheetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val imagePaths = intent.getStringArrayListExtra(EXTRA_IMAGE_PATHS) ?: arrayListOf()
        if (imagePaths.isEmpty()) {
            Toast.makeText(
                this,
                "Please drag and drop image files onto this script or provide them as arguments.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                PassportA4Screen(
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
fun PassportA4Screen(
    initialPaths: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imagePaths by remember { mutableStateOf(initialPaths) }
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }

    // Sliders state
    var startSlot by remember { mutableFloatStateOf(1f) }
    var slotsPerImage by remember { mutableFloatStateOf(3f) }

    // Background removal settings
    var enableBackgroundRemoval by remember { mutableStateOf(true) }
    var selectedBackground by remember { mutableStateOf(PassportBackground.SKY_BLUE) }

    var sheetBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    // Load initial Bitmaps
    LaunchedEffect(imagePaths) {
        val loaded = imagePaths.mapNotNull { path ->
            PassportPhotoProcessor.loadBitmap(context, Uri.fromFile(File(path)))
        }
        bitmaps = loaded
    }

    // uCrop Launcher
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val croppedUri = UCrop.getOutput(res.data!!)
            croppedUri?.let { uri ->
                scope.launch {
                    val updated = PassportPhotoProcessor.loadBitmap(context, uri)
                    if (updated != null && selectedImageIndex in bitmaps.indices) {
                        val list = bitmaps.toMutableList()
                        list[selectedImageIndex] = updated
                        bitmaps = list
                    }
                }
            }
        }
    }

    // Picker Launcher for adding more images
    val addImagesPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val newPaths = mutableListOf<String>()
                uris.forEach { uri ->
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val cacheFile = File(
                            context.cacheDir,
                            "imported_passport_${System.currentTimeMillis()}_${(0..999).random()}.jpg"
                        )
                        FileOutputStream(cacheFile).use { out ->
                            inputStream?.copyTo(out)
                        }
                        newPaths.add(cacheFile.absolutePath)
                    } catch (_: Exception) {}
                }
                if (newPaths.isNotEmpty()) {
                    imagePaths = imagePaths + newPaths
                }
            }
        }
    }

    fun computeSlots(list: List<Bitmap>, start: Int, perImage: Int): Map<Int, Bitmap> {
        val map = mutableMapOf<Int, Bitmap>()
        var currentSlot = start
        list.forEach { bmp ->
            repeat(perImage) {
                if (currentSlot in 1..36) {
                    map[currentSlot] = bmp
                }
                currentSlot++
            }
        }
        return map
    }

    fun generateSheet() {
        if (bitmaps.isEmpty()) return
        scope.launch {
            isGenerating = true

            // Process bitmaps with ML Kit background removal if enabled
            val processedBitmaps = bitmaps.map { bmp ->
                if (enableBackgroundRemoval) {
                    val foreground = PassportPhotoProcessor.removeBackground(bmp)
                    if (foreground != null) {
                        PassportPhotoProcessor.applyBackground(foreground, selectedBackground)
                    } else {
                        bmp
                    }
                } else {
                    bmp
                }
            }

            val slotsMap = computeSlots(processedBitmaps, startSlot.toInt(), slotsPerImage.toInt())
            sheetBitmap = PassportPhotoProcessor.createA4PassportSheet(slotsMap)
            isGenerating = false
        }
    }

    LaunchedEffect(bitmaps) {
        generateSheet()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "A4 Passport Photo Sheet",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                text = "Loaded Images (${bitmaps.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            // Image Carousel with Add Button
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(bitmaps) { index, bmp ->
                    val isSelected = index == selectedImageIndex
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(65.dp, 85.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedImageIndex = index },
                        contentScale = ContentScale.Crop
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .size(65.dp, 85.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                addImagesPickerLauncher.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Images",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    if (selectedImageIndex in imagePaths.indices) {
                        val src = Uri.fromFile(File(imagePaths[selectedImageIndex]))
                        val dest = Uri.fromFile(
                            File(context.cacheDir, "passport_crop_${System.currentTimeMillis()}.png")
                        )
                        val intent = UCrop.of(src, dest)
                            .withAspectRatio(30f, 40f)
                            .getIntent(context)
                        cropLauncher.launch(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Crop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Crop Selected Image")
            }

            // Global Sequence with Sliders & Background Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Global Sequence",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Starting Slot: 1 to 36
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Starting Slot", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Slot ${startSlot.toInt()}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = startSlot,
                            onValueChange = { startSlot = it },
                            valueRange = 1f..36f,
                            steps = 34
                        )
                    }

                    // Slots Per Image: 1 to 6
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Slots Per Image", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${slotsPerImage.toInt()} slots",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = slotsPerImage,
                            onValueChange = { slotsPerImage = it },
                            valueRange = 1f..6f,
                            steps = 4
                        )
                    }

                    // Background Removal Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Remove Background (ML Kit)",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (enableBackgroundRemoval) "Enabled" else "Using original photo background",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enableBackgroundRemoval,
                            onCheckedChange = { enableBackgroundRemoval = it }
                        )
                    }

                    // Background Color Selection (Visible when enabled)
                    if (enableBackgroundRemoval) {
                        Text(
                            text = "Background Color: ${selectedBackground.title}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(PassportBackground.entries) { bg ->
                                val isSelected = bg == selectedBackground
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(bg.startColor))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedBackground = bg }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { generateSheet() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply Global Sequence")
                    }
                }
            }

            // A4 Sheet Preview & Action Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "A4 Sheet Preview (6 × 6 Grid)",
                        fontWeight = FontWeight.Bold
                    )
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
                            sheetBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "A4 Sheet",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = {
                            sheetBitmap?.let { bmp ->
                                scope.launch {
                                    val uri = PassportPhotoProcessor.saveToDownloads(context, bmp)
                                    Toast.makeText(
                                        context,
                                        if (uri != null) "Saved to Downloads folder" else "Failed to save",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Download Page")
                        }

                        IconButton(onClick = {
                            sheetBitmap?.let { bmp ->
                                scope.launch {
                                    val file = PassportPhotoProcessor.saveCacheFile(context, bmp)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val printIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        setPackage("com.noco.print")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        // Ensures NokoPrint gets its own Recents app card
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                    }
                                    try {
                                        context.startActivity(printIntent)
                                    } catch (_: Exception) {
                                        printIntent.setPackage("com.nokoprint")
                                        try {
                                            context.startActivity(printIntent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "NokoPrint app not installed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Print, contentDescription = "Print Page")
                        }

                        IconButton(onClick = {
                            sheetBitmap?.let { bmp ->
                                scope.launch {
                                    val file = PassportPhotoProcessor.saveCacheFile(context, bmp)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        setPackage("com.whatsapp")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(shareIntent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "WhatsApp Share")
                        }
                    }
                }
            }
        }
    }
}
