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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

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
    var selectedSlot by remember { mutableStateOf<PrintPosition?>(null) }
    var paperSize by remember { mutableStateOf(PaperSize.A4) }
    var isStacked by remember { mutableStateOf(false) }

    var flowState by remember { mutableStateOf(FlowState.PAGE_OVERVIEW) }
    var isProcessing by remember { mutableStateOf(false) }
    var isGeneratingPreview by remember { mutableStateOf(false) }

    var currentSourceUri by remember { mutableStateOf<Uri?>(null) }
    var workspaceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var splitFront by remember { mutableStateOf<Bitmap?>(null) }
    var splitBack by remember { mutableStateOf<Bitmap?>(null) }
    var finalPrintBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var draggingPos by remember { mutableStateOf<PrintPosition?>(null) }
    var dragFingerOffset by remember { mutableStateOf(Offset.Zero) }
    val slotBounds = remember { mutableStateMapOf<PrintPosition, Rect>() }
    var currentHoverTarget by remember { mutableStateOf<PrintPosition?>(null) }

    LaunchedEffect(pageSlots.toMap(), paperSize, isStacked) {
        if (pageSlots.isEmpty()) {
            finalPrintBitmap = null
            return@LaunchedEffect
        }
        isGeneratingPreview = true
        delay(500)
        try {
            finalPrintBitmap = IdStudioLogic.createMultiPrintLayout(context, pageSlots.toMap(), paperSize, isStacked)
        } finally {
            isGeneratingPreview = false
        }
    }

    val handleFileSelection = { uri: Uri ->
        flowState = FlowState.PREVIEW_READY
        isProcessing = true
        scope.launch {
            try {
                workspaceBitmap = null
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val isPdf = mimeType.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")

                val bitmap = if (isPdf) IdStudioLogic.renderPdfFirstPageInternal(context, uri)
                else IdStudioLogic.loadBitmapInternal(context, uri)

                workspaceBitmap = bitmap
                workspaceBitmap?.let {
                    currentSourceUri = IdStudioLogic.saveBitmapToTempInternal(context, it)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading file", Toast.LENGTH_SHORT).show()
                flowState = FlowState.PAGE_OVERVIEW
            } finally { isProcessing = false }
        }
    }

    LaunchedEffect(initialUri, initialUris) {
        val target = initialUris?.firstOrNull() ?: initialUri
        target?.let { selectedSlot = PrintPosition.POS_1; handleFileSelection(it) }
    }

    val selectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleFileSelection(it) }
    }

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            UCrop.getOutput(result.data!!)?.let { uri ->
                scope.launch {
                    isProcessing = true
                    workspaceBitmap = IdStudioLogic.loadBitmapInternal(context, uri)
                    flowState = FlowState.CROPPED
                    isProcessing = false
                }
            }
        }
    }

    BackHandler {
        if (flowState == FlowState.PAGE_OVERVIEW) onBack() else flowState = FlowState.PAGE_OVERVIEW
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
                            FlowState.PAGE_OVERVIEW -> "ID Card Splitter & Multi-Print"
                            FlowState.PREVIEW_READY -> "Step 1: Crop ID Area"
                            FlowState.CROPPED -> "Step 2: Slice Front & Back"
                            FlowState.SLICED -> "Step 3: Preview & Confirm"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (flowState == FlowState.PAGE_OVERVIEW) onBack() else flowState = FlowState.PAGE_OVERVIEW
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
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
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val hit = slotBounds.entries.find { it.value.contains(offset) }?.key
                                            if (hit != null && pageSlots.containsKey(hit)) {
                                                draggingPos = hit
                                                dragFingerOffset = offset
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragFingerOffset += amount
                                            currentHoverTarget = slotBounds.entries.find {
                                                it.key != draggingPos && it.value.contains(dragFingerOffset)
                                            }?.key
                                        },
                                        onDragEnd = {
                                            if (draggingPos != null && currentHoverTarget != null) {
                                                val fromData = pageSlots[draggingPos!!]
                                                val toData = pageSlots[currentHoverTarget!!]
                                                if (fromData != null) {
                                                    if (toData != null) {
                                                        pageSlots[draggingPos!!] = toData
                                                        pageSlots[currentHoverTarget!!] = fromData
                                                    } else {
                                                        pageSlots[currentHoverTarget!!] = fromData
                                                        pageSlots.remove(draggingPos!!)
                                                    }
                                                }
                                            }
                                            draggingPos = null; currentHoverTarget = null
                                        },
                                        onDragCancel = { draggingPos = null; currentHoverTarget = null }
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F9)),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Paper Size", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                            Spacer(Modifier.height(4.dp))
                                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                                PaperSize.entries.forEachIndexed { i, s ->
                                                    SegmentedButton(
                                                        selected = paperSize == s,
                                                        onClick = { paperSize = s },
                                                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                                                        label = { Text(s.name, fontSize = 11.sp) }
                                                    )
                                                }
                                            }
                                        }
                                        Column(Modifier.weight(1.3f)) {
                                            Text("Layout Arrangement", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                            Spacer(Modifier.height(4.dp))
                                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                                SegmentedButton(
                                                    selected = !isStacked,
                                                    onClick = { isStacked = false },
                                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                                    label = { Text("6 Horizontal", fontSize = 10.sp) }
                                                )
                                                SegmentedButton(
                                                    selected = isStacked,
                                                    onClick = { isStacked = true },
                                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                                    label = { Text("5 Stacked", fontSize = 10.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            IdCompositionGrid(
                                isStacked = isStacked,
                                slots = pageSlots.toMap(),
                                draggingPos = draggingPos,
                                dragOffset = dragFingerOffset,
                                slotBounds = slotBounds,
                                currentHoverTarget = currentHoverTarget,
                                onSlotClick = { pos ->
                                    selectedSlot = pos
                                    selectLauncher.launch(arrayOf("image/*", "application/pdf"))
                                },
                                onClearSlot = { pageSlots.remove(it) }
                            )

                            FinalPreviewCard(
                                bitmap = finalPrintBitmap,
                                isGenerating = isGeneratingPreview,
                                onSave = { finalPrintBitmap?.let { IdStudioLogic.saveToDownloadsInternal(context, it, "ID_Compose") } },
                                onShare = { finalPrintBitmap?.let { IdStudioLogic.shareImageInternal(context, it) } }
                            )

                            Spacer(Modifier.height(32.dp))
                        }
                    }
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PreviewCard(workspaceBitmap, isProcessing)

                        if (state == FlowState.PREVIEW_READY) {
                            Button(
                                onClick = {
                                    currentSourceUri?.let { uri ->
                                        val dest = Uri.fromFile(File(context.cacheDir, "crop_${System.currentTimeMillis()}.png"))
                                        val options = UCrop.Options().apply {
                                            withAspectRatio(3.1f, 1f)
                                            setFreeStyleCropEnabled(true)
                                            setHideBottomControls(false)
                                            setToolbarColor(android.graphics.Color.WHITE)
                                            setStatusBarColor(android.graphics.Color.WHITE)
                                            setToolbarWidgetColor(android.graphics.Color.BLACK)
                                            setToolbarTitle("Crop ID (Front & Back)")
                                        }
                                        val intent = UCrop.of(uri, dest)
                                            .withOptions(options)
                                            .getIntent(context)
                                        cropLauncher.launch(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = !isProcessing
                            ) {
                                Icon(Icons.Default.Crop, null)
                                Spacer(Modifier.width(8.dp))
                                Text("CROP ID AREA (3.1 : 1)")
                            }
                        }

                        if (state == FlowState.CROPPED) {
                            Button(
                                onClick = {
                                    workspaceBitmap?.let { bmp ->
                                        splitFront = Bitmap.createBitmap(bmp, 0, 0, bmp.width / 2, bmp.height)
                                        splitBack = Bitmap.createBitmap(bmp, bmp.width / 2, 0, bmp.width / 2, bmp.height)
                                        flowState = FlowState.SLICED
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = !isProcessing
                            ) {
                                Icon(Icons.Default.ContentCut, null)
                                Spacer(Modifier.width(8.dp))
                                Text("EXTRACT SIDES (SPLIT 50/50)")
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
                                    selectedSlot?.let { pos ->
                                        if (splitFront != null && splitBack != null) {
                                            pageSlots[pos] = SlotData(splitFront!!, splitBack!!)
                                        }
                                    }
                                    flowState = FlowState.PAGE_OVERVIEW
                                },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                shape = RoundedCornerShape(16.dp),
                                enabled = !isProcessing
                            ) {
                                Icon(Icons.Default.Check, null)
                                Spacer(Modifier.width(8.dp))
                                Text("CONFIRM SLOT POSITION")
                            }
                        }
                    }
                }
            }

            // Animated Drag-and-Drop Floating Ghost
            draggingPos?.let { pos ->
                pageSlots[pos]?.let { data ->
                    Surface(
                        modifier = Modifier
                            .size(140.dp, 44.dp)
                            .offset { IntOffset(dragFingerOffset.x.roundToInt() - 200, dragFingerOffset.y.roundToInt() - 70) }
                            .graphicsLayer { rotationZ = -2f; scaleX = 1.08f; scaleY = 1.08f }
                            .shadow(16.dp, RoundedCornerShape(10.dp))
                            .alpha(0.9f),
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(Modifier.fillMaxSize().padding(4.dp)) {
                            Image(data.front.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Fit)
                            Image(data.back.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Fit)
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