package org.fossify.filemanager.customtools.idcardsplitter

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.filemanager.customtools.sharing.NokoPrintHelper
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractIdCardScreen(
    initialUri: Uri? = null,
    initialUris: List<Uri>? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pageSlots = remember { mutableStateMapOf<PrintPosition, SlotData>() }
    var currentSlot by remember { mutableStateOf(PrintPosition.POS_1) }
    var paperSize by remember { mutableStateOf(PaperSize.A4) }
    var isStacked by remember { mutableStateOf(false) }
    var pageBg by remember { mutableStateOf<PageBackground>(PageBackground.White) }
    var optionsExpanded by remember { mutableStateOf(false) }

    var flowState by remember { mutableStateOf(FlowState.PAGE_OVERVIEW) }
    var isProcessing by remember { mutableStateOf(false) }
    var isGeneratingPreview by remember { mutableStateOf(false) }

    var currentSourceUri by remember { mutableStateOf<Uri?>(null) }
    var workspaceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var splitFront by remember { mutableStateOf<Bitmap?>(null) }
    var splitBack by remember { mutableStateOf<Bitmap?>(null) }
    var finalPrintBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val positions: List<PrintPosition> = if (isStacked) {
        listOf(PrintPosition.POS_1, PrintPosition.POS_2, PrintPosition.POS_3, PrintPosition.POS_4, PrintPosition.POS_5)
    } else {
        PrintPosition.entries
    }

    LaunchedEffect(pageSlots.toMap(), paperSize, isStacked, pageBg) {
        if (pageSlots.isEmpty()) {
            finalPrintBitmap = null
            return@LaunchedEffect
        }
        isGeneratingPreview = true
        delay(350L)
        try {
            finalPrintBitmap = IdStudioLogic.createMultiPrintLayout(context, pageSlots.toMap(), paperSize, isStacked, pageBg)
        } finally {
            isGeneratingPreview = false
        }
    }

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            UCrop.getOutput(result.data!!)?.let { uri ->
                scope.launch {
                    isProcessing = true
                    val croppedBmp = IdStudioLogic.loadBitmapInternal(context, uri)
                    workspaceBitmap = croppedBmp

                    if (croppedBmp != null) {
                        if (isStacked) {
                            splitFront = Bitmap.createBitmap(croppedBmp, 0, 0, croppedBmp.width / 2, croppedBmp.height)
                            splitBack = Bitmap.createBitmap(croppedBmp, croppedBmp.width / 2, 0, croppedBmp.width / 2, croppedBmp.height)
                            flowState = FlowState.SLICED
                        } else {
                            splitFront = Bitmap.createBitmap(croppedBmp, 0, 0, croppedBmp.width / 2, croppedBmp.height)
                            splitBack = Bitmap.createBitmap(croppedBmp, croppedBmp.width / 2, 0, croppedBmp.width / 2, croppedBmp.height)
                            pageSlots[currentSlot] = SlotData(splitFront!!, splitBack!!)
                            flowState = FlowState.PAGE_OVERVIEW
                        }
                    } else {
                        flowState = FlowState.PAGE_OVERVIEW
                    }
                    isProcessing = false
                }
            }
        }
    }

    val launchCropForUri = { uri: Uri ->
        val dest = Uri.fromFile(File(context.cacheDir, "crop_${System.currentTimeMillis()}.png"))
        val options = UCrop.Options().apply {
            withAspectRatio(3.1f, 1f)
            setFreeStyleCropEnabled(true)
            setHideBottomControls(false)
            setToolbarColor(android.graphics.Color.WHITE)
            setStatusBarColor(android.graphics.Color.WHITE)
            setToolbarWidgetColor(android.graphics.Color.BLACK)
            setToolbarTitle("Crop ID Area")
        }
        val intent = UCrop.of(uri, dest).withOptions(options).getIntent(context)
        cropLauncher.launch(intent)
    }

    // Direct image loader (bypasses automatic crop, loads directly into page overview)
    val handleFileSelectionDirect = { uri: Uri ->
        isProcessing = true
        scope.launch {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val isPdf = mimeType.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")

                val bitmap = if (isPdf) IdStudioLogic.renderPdfFirstPageInternal(context, uri)
                else IdStudioLogic.loadBitmapInternal(context, uri)

                if (bitmap != null) {
                    workspaceBitmap = bitmap
                    currentSourceUri = IdStudioLogic.saveBitmapToTempInternal(context, bitmap)

                    val front = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width / 2, bitmap.height)
                    val back = Bitmap.createBitmap(bitmap, bitmap.width / 2, 0, bitmap.width / 2, bitmap.height)
                    pageSlots[currentSlot] = SlotData(front, back)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading file", Toast.LENGTH_SHORT).show()
            } finally {
                flowState = FlowState.PAGE_OVERVIEW
                isProcessing = false
            }
        }
    }

    LaunchedEffect(initialUri, initialUris) {
        val target = initialUris?.firstOrNull() ?: initialUri
        target?.let {
            currentSlot = PrintPosition.POS_1
            handleFileSelectionDirect(it)
        }
    }

    val selectCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleFileSelectionDirect(it) }
    }

    val selectBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                val bgBmp = IdStudioLogic.loadBitmapInternal(context, it)
                if (bgBmp != null) {
                    pageBg = PageBackground.CustomImage(bgBmp)
                }
            }
        }
    }

    BackHandler {
        if (flowState == FlowState.PAGE_OVERVIEW) {
            onBack()
        } else {
            flowState = FlowState.PAGE_OVERVIEW
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (flowState) {
                            FlowState.PAGE_OVERVIEW -> "ID Card Splitter & Layout"
                            FlowState.PREVIEW_READY -> "Step 1: Crop ID Area"
                            FlowState.CROPPED -> "Step 2: Confirm Layout"
                            FlowState.SLICED -> "Step 2: Flip & Confirm Stack"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (flowState == FlowState.PAGE_OVERVIEW) onBack()
                        else flowState = FlowState.PAGE_OVERVIEW
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
        ) {
            AnimatedContent(targetState = flowState, label = "Flow") { state ->
                when (state) {
                    FlowState.PAGE_OVERVIEW -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { optionsExpanded = !optionsExpanded },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Page Configuration", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                            Text("${paperSize.name} • ${if (isStacked) "5 Stacked" else "6 Horizontal"}", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                        IconButton(onClick = { optionsExpanded = !optionsExpanded }, modifier = Modifier.size(28.dp)) {
                                            Icon(if (optionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Expand Options", tint = Color.Gray)
                                        }
                                    }

                                    AnimatedVisibility(visible = optionsExpanded) {
                                        Column(
                                            modifier = Modifier.padding(top = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("Paper Size", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                    Spacer(Modifier.height(4.dp))
                                                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                                        PaperSize.entries.forEachIndexed { i, s ->
                                                            SegmentedButton(
                                                                selected = paperSize == s,
                                                                onClick = { paperSize = s },
                                                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                                                                label = { Text(s.name, fontSize = 10.sp) }
                                                            )
                                                        }
                                                    }
                                                }
                                                Column(Modifier.weight(1.3f)) {
                                                    Text("Layout Arrangement", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                    Spacer(Modifier.height(4.dp))
                                                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                                        SegmentedButton(
                                                            selected = !isStacked,
                                                            onClick = { isStacked = false },
                                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                                            label = { Text("6 Horiz", fontSize = 10.sp) }
                                                        )
                                                        SegmentedButton(
                                                            selected = isStacked,
                                                            onClick = { isStacked = true },
                                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                                            label = { Text("5 Stack", fontSize = 10.sp) }
                                                        )
                                                    }
                                                }
                                            }

                                            BackgroundOptionRow(
                                                currentBg = pageBg,
                                                onSelectWhite = { pageBg = PageBackground.White },
                                                onSelectGallery = { selectBgLauncher.launch(arrayOf("image/*")) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Slider selector with dedicated crop icon button replacing the old delete icon
                            SlotSliderSelector(
                                positions = positions,
                                selectedPosition = currentSlot,
                                slots = pageSlots.toMap(),
                                onPositionChanged = { newTargetSlot ->
                                    if (currentSlot != newTargetSlot) {
                                        val sourceData = pageSlots[currentSlot]
                                        val targetData = pageSlots[newTargetSlot]

                                        if (sourceData != null) {
                                            pageSlots[newTargetSlot] = sourceData
                                            if (targetData != null) {
                                                pageSlots[currentSlot] = targetData
                                            } else {
                                                pageSlots.remove(currentSlot)
                                            }
                                        }
                                    }
                                    currentSlot = newTargetSlot
                                },
                                onAddOrReplace = { selectCardLauncher.launch(arrayOf("image/*", "application/pdf")) },
                                onCropSlot = {
                                    val slotData = pageSlots[currentSlot]
                                    if (slotData != null) {
                                        scope.launch {
                                            val combinedW = slotData.front.width + slotData.back.width
                                            val combinedH = maxOf(slotData.front.height, slotData.back.height)
                                            val combined = Bitmap.createBitmap(combinedW, combinedH, Bitmap.Config.ARGB_8888)
                                            val canvas = android.graphics.Canvas(combined)
                                            canvas.drawBitmap(slotData.front, 0f, 0f, null)
                                            canvas.drawBitmap(slotData.back, slotData.front.width.toFloat(), 0f, null)

                                            val tempUri = IdStudioLogic.saveBitmapToTempInternal(context, combined)
                                            launchCropForUri(tempUri)
                                        }
                                    } else currentSourceUri?.let { uri ->
                                        launchCropForUri(uri)
                                    }
                                }
                            )

                            FinalPreviewCard(
                                bitmap = finalPrintBitmap,
                                isGenerating = isGeneratingPreview,
                                onSave = { finalPrintBitmap?.let { IdStudioLogic.saveToDownloadsInternal(context, it, "ID_Compose") } },
                                onShare = { finalPrintBitmap?.let { IdStudioLogic.shareImageInternal(context, it) } },
                                onPrint = {
                                    finalPrintBitmap?.let { bmp ->
                                        scope.launch {
                                            val cacheFolder = File(context.cacheDir, "id_print_sheets").apply { mkdirs() }
                                            val tempFile = File(cacheFolder, "ID_Multi_${System.currentTimeMillis()}.png")
                                            FileOutputStream(tempFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                            NokoPrintHelper.print(context, listOf(tempFile.absolutePath))
                                        }
                                    }
                                }
                            )

                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PreviewCard(workspaceBitmap, isProcessing)

                        if (state == FlowState.PREVIEW_READY) {
                            Button(
                                onClick = {
                                    currentSourceUri?.let { uri -> launchCropForUri(uri) }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = !isProcessing
                            ) {
                                Icon(Icons.Default.Crop, null)
                                Spacer(Modifier.width(8.dp))
                                Text("CROP ID AREA (3.1 : 1)")
                            }
                        }

                        if (state == FlowState.SLICED) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    ResultItem(Modifier.fillMaxWidth(), "Front Side", splitFront)
                                    FlipControls(
                                        { splitFront = IdStudioLogic.flipBitmap(splitFront!!, true) },
                                        { splitFront = IdStudioLogic.flipBitmap(splitFront!!, false) }
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    ResultItem(Modifier.fillMaxWidth(), "Back Side", splitBack)
                                    FlipControls(
                                        { splitBack = IdStudioLogic.flipBitmap(splitBack!!, true) },
                                        { splitBack = IdStudioLogic.flipBitmap(splitBack!!, false) }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (splitFront != null && splitBack != null) {
                                        pageSlots[currentSlot] = SlotData(splitFront!!, splitBack!!)
                                    }
                                    flowState = FlowState.PAGE_OVERVIEW
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isProcessing
                            ) {
                                Icon(Icons.Default.Check, null)
                                Spacer(Modifier.width(8.dp))
                                Text("CONFIRM STACK POSITION")
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}