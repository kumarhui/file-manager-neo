package org.fossify.filemanager.customtools.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.yalantis.ucrop.UCrop
import org.fossify.filemanager.R
import org.fossify.filemanager.customtools.CustomToolRunner
import org.fossify.filemanager.customtools.idcard.IdCardActivity
import org.fossify.filemanager.customtools.idcardsplitter.IdCardSplitterActivity
import org.fossify.filemanager.customtools.passport.PassportA4SheetActivity
import org.fossify.filemanager.customtools.preview.CustomRenameDialog
import org.fossify.filemanager.customtools.preview.ResultDialog
import org.fossify.filemanager.customtools.preview.WordSplitRenameDialog
import org.fossify.filemanager.customtools.sharing.JuganuaHelper
import org.fossify.filemanager.customtools.sharing.NokoPrintHelper
import org.fossify.filemanager.customtools.sharing.WhatsAppHelper
import java.io.File

class CustomToolsDialogFragment : DialogFragment() {

    private var imagePath: String = ""
    private var imagePaths: ArrayList<String> = arrayListOf()

    private val cropImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val croppedUri = UCrop.getOutput(result.data!!)
                if (croppedUri != null) {
                    dismiss()
                    val path = croppedUri.path ?: ""
                    ResultDialog.show(requireContext(), path, "Cropped Image")
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR && result.data != null) {
                val cropError = UCrop.getError(result.data!!)
                Toast.makeText(requireContext(), cropError?.message ?: "Crop failed", Toast.LENGTH_SHORT).show()
            }
        }

    companion object {
        private const val ARG_IMAGE_PATH = "image_path"
        private const val ARG_IMAGE_PATHS = "image_paths"
        private const val TAG = "CustomToolsDialog"

        fun show(activity: FragmentActivity, imagePaths: List<String>) {
            if (activity.isFinishing || activity.isDestroyed) return
            val manager = activity.supportFragmentManager
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return

            CustomToolsDialogFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_IMAGE_PATHS, ArrayList(imagePaths))
                    if (imagePaths.isNotEmpty()) {
                        putString(ARG_IMAGE_PATH, imagePaths.first())
                    }
                }
            }.show(manager, TAG)
        }

        fun show(activity: FragmentActivity, imagePath: String) {
            show(activity, listOf(imagePath))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imagePaths = arguments?.getStringArrayList(ARG_IMAGE_PATHS) ?: arrayListOf()
        if (imagePaths.isEmpty()) {
            val single = arguments?.getString(ARG_IMAGE_PATH).orEmpty()
            if (single.isNotEmpty()) imagePaths.add(single)
        }
        imagePath = imagePaths.firstOrNull().orEmpty()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    CustomToolsContent(
                        selectedCount = imagePaths.size,
                        onDismiss = { dismiss() },
                        onToolClick = { toolId -> handleToolClick(toolId) }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.50f)
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun handleToolClick(toolId: ToolId) {
        if (imagePath.isBlank() && imagePaths.isEmpty()) return

        when (toolId) {
            ToolId.NOKOPRINT -> {
                val pathsToPrint = if (imagePaths.isNotEmpty()) imagePaths else listOf(imagePath)
                val hasPdf = pathsToPrint.any { it.endsWith(".pdf", ignoreCase = true) }
                if (hasPdf) {
                    val dpiOptions = arrayOf(
                        "300 DPI (Standard Print)",
                        "450 DPI (Enhanced Clarity)",
                        "600 DPI (High Resolution)",
                        "800 DPI (Fine Detail)",
                        "1000 DPI (Super High-Res)",
                        "1200 DPI (Ultra Sharp Max)"
                    )
                    val dpiValues = intArrayOf(300, 450, 600, 800, 1000, 1200)
                    var selectedDpi = 300

                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Select Extraction Quality")
                        .setSingleChoiceItems(dpiOptions, 0) { _, which ->
                            selectedDpi = dpiValues[which]
                        }
                        .setPositiveButton("Extract & Print") { _, _ ->
                            dismiss()
                            NokoPrintHelper.extractPdfToTempAndPrint(requireContext(), pathsToPrint, selectedDpi)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    dismiss()
                    NokoPrintHelper.print(requireContext(), pathsToPrint)
                }
            }

            ToolId.WHATSAPP -> {
                val pathsToShare = if (imagePaths.isNotEmpty()) imagePaths else listOf(imagePath)
                if (WhatsAppHelper.share(requireContext(), pathsToShare)) {
                    dismiss()
                }
            }

            ToolId.PASSPORT_PHOTO -> {
                dismiss()
                val intent = Intent(requireContext(), PassportA4SheetActivity::class.java).apply {
                    putStringArrayListExtra(PassportA4SheetActivity.EXTRA_IMAGE_PATHS, ArrayList(imagePaths))
                }
                requireContext().startActivity(intent)
            }

            ToolId.ID_CARD -> {
                dismiss()
                val intent = Intent(requireContext(), IdCardActivity::class.java).apply {
                    putStringArrayListExtra(IdCardActivity.EXTRA_IMAGE_PATHS, ArrayList(imagePaths))
                }
                requireContext().startActivity(intent)
            }

            ToolId.PDF_UNLOCKER -> {
                dismiss()
                val uri = Uri.fromFile(File(imagePath))
                org.fossify.filemanager.customtools.pdfunlocker.PdfUnlockerDialog.show(requireContext(), uri)
            }

            ToolId.ID_CARD_SPLITTER -> {
                dismiss()
                val intent = Intent(requireContext(), IdCardSplitterActivity::class.java).apply {
                    putStringArrayListExtra(IdCardSplitterActivity.EXTRA_IMAGE_PATHS, ArrayList(imagePaths))
                }
                requireContext().startActivity(intent)
            }

            ToolId.CROP_IMAGE -> {
                val sourceUri = Uri.fromFile(File(imagePath))
                val destinationUri = Uri.fromFile(
                    File(requireContext().cacheDir, "crop_${System.currentTimeMillis()}.png")
                )
                val uCropIntent = UCrop.of(sourceUri, destinationUri)
                    .withOptions(UCrop.Options().apply {
                        setFreeStyleCropEnabled(true)
                        setStatusBarColor(android.graphics.Color.WHITE)
                        setToolbarColor(android.graphics.Color.WHITE)
                        setToolbarWidgetColor(android.graphics.Color.BLACK)
                    })
                    .getIntent(requireContext())
                cropImageLauncher.launch(uCropIntent)
            }

            ToolId.COMPRESS_IMAGE -> {
                dismiss()
                ImageCompressionDialog.show(requireContext(), imagePath)
            }

            ToolId.CONVERT_PDF -> {
                val result = CustomToolRunner.convertPdf(requireContext(), imagePath)
                if (result.success && result.outputPath != null) {
                    dismiss()
                    ResultDialog.show(requireContext(), result.outputPath, "PDF")
                }
            }

            ToolId.WORD_SPLIT_RENAME -> {
                dismiss()
                WordSplitRenameDialog.show(requireContext(), imagePaths)
            }

            ToolId.CUSTOM_RENAME -> {
                dismiss()
                CustomRenameDialog.show(requireContext(), imagePaths)
            }

            ToolId.JUGANUA -> {
                if (JuganuaHelper.open(requireContext(), imagePath)) dismiss()
            }
        }
    }
}

object CustomToolsSheet {
    fun show(context: android.content.Context, imagePaths: List<String>) {
        val activity = context as? FragmentActivity ?: return
        CustomToolsDialogFragment.show(activity, imagePaths)
    }

    fun show(context: android.content.Context, imagePath: String) {
        show(context, listOf(imagePath))
    }
}

data class UnifiedTool(
    val id: ToolId,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val iconTint: Color,
    val containerColor: Color,
    val borderColor: Color = Color(0xFFE5E7EB)
)

@Composable
private fun CustomToolsContent(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onToolClick: (ToolId) -> Unit
) {
    val context = LocalContext.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Specified layout order
    val allTools = listOf(
        UnifiedTool(
            id = ToolId.NOKOPRINT,
            title = "NokoPrint",
            subtitle = "Extract & print documents",
            iconRes = R.drawable.ic_tool_print,
            iconTint = Color(0xFF0284C7),
            containerColor = Color(0xFFF0F9FF),
            borderColor = Color(0xFFBAE6FD)
        ),
        UnifiedTool(
            id = ToolId.WHATSAPP,
            title = "WhatsApp",
            subtitle = "Send files directly",
            iconRes = R.drawable.ic_tool_whatsapp,
            iconTint = Color(0xFF16A34A),
            containerColor = Color(0xFFF0FDF4),
            borderColor = Color(0xFFBBF7D0)
        ),
        UnifiedTool(
            id = ToolId.PASSPORT_PHOTO,
            title = "Passport",
            subtitle = "30 × 40 mm photo sheet",
            iconRes = R.drawable.ic_tool_passport,
            iconTint = Color(0xFF7C3AED),
            containerColor = Color(0xFFF5F3FF),
            borderColor = Color(0xFFDDD6FE)
        ),
        UnifiedTool(
            id = ToolId.ID_CARD,
            title = "ID Card",
            subtitle = "Standard 85.6 × 54 mm A4",
            iconRes = R.drawable.ic_tool_id_card,
            iconTint = Color(0xFF2563EB),
            containerColor = Color(0xFFEFF6FF),
            borderColor = Color(0xFFBFDBFE)
        ),
        UnifiedTool(
            id = ToolId.PDF_UNLOCKER,
            title = "Unlocker",
            subtitle = "Decrypt PDF files",
            iconRes = R.drawable.ic_tool_pdf,
            iconTint = Color(0xFFDC2626),
            containerColor = Color(0xFFFEF2F2),
            borderColor = Color(0xFFFECACA)
        ),
        UnifiedTool(
            id = ToolId.ID_CARD_SPLITTER,
            title = "ID Splitter",
            subtitle = "Front & back slot layout",
            iconRes = R.drawable.ic_tool_split,
            iconTint = Color(0xFF0D9488),
            containerColor = Color(0xFFF0FDFA),
            borderColor = Color(0xFF99F6E4)
        ),
        UnifiedTool(
            id = ToolId.CROP_IMAGE,
            title = "Crop Image",
            subtitle = "Adjust image boundaries",
            iconRes = R.drawable.ic_tool_crop,
            iconTint = Color(0xFFEA580C),
            containerColor = Color(0xFFFFF7ED),
            borderColor = Color(0xFFFFEDD5)
        ),
        UnifiedTool(
            id = ToolId.COMPRESS_IMAGE,
            title = "Compress",
            subtitle = "Reduce image file size",
            iconRes = R.drawable.ic_tool_compress,
            iconTint = Color(0xFF4F46E5),
            containerColor = Color(0xFFEEF2FF),
            borderColor = Color(0xFFC7D2FE)
        ),
        UnifiedTool(
            id = ToolId.CONVERT_PDF,
            title = "To PDF",
            subtitle = "Convert images into PDF",
            iconRes = R.drawable.ic_tool_pdf,
            iconTint = Color(0xFFBE123C),
            containerColor = Color(0xFFFFF1F2),
            borderColor = Color(0xFFFECDD3)
        ),
        UnifiedTool(
            id = ToolId.WORD_SPLIT_RENAME,
            title = "Word Split",
            subtitle = "Batch tokenized rename",
            iconRes = R.drawable.ic_tool_text_rename,
            iconTint = Color(0xFF0891B2),
            containerColor = Color(0xFFECFEFF),
            borderColor = Color(0xFFA5F3FC)
        ),
        UnifiedTool(
            id = ToolId.CUSTOM_RENAME,
            title = "Paste Rename",
            subtitle = "Multi-line clipboard rename",
            iconRes = R.drawable.ic_tool_paste,
            iconTint = Color(0xFF475569),
            containerColor = Color(0xFFF8FAFC),
            borderColor = Color(0xFFE2E8F0)
        ),
        UnifiedTool(
            id = ToolId.JUGANUA,
            title = "Juganua",
            subtitle = "Open inside Juganua",
            iconRes = R.drawable.ic_tool_juganua,
            iconTint = Color(0xFFD97706),
            containerColor = Color(0xFFFFFBEB),
            borderColor = Color(0xFFFDE68A)
        )
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 10.dp,
                    bottom = 16.dp + navBarBottom
                )
        ) {
            // Drag handle pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFE2E8F0))
                )
            }

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Tools",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = if (selectedCount > 1) "$selectedCount files selected • Long-press for details" else "Select tool • Long-press for details",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF475569)
                    )
                }
            }

            // 4-column modern icon grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(allTools) { tool ->
                    ModernToolButton(
                        tool = tool,
                        onClick = { onToolClick(tool.id) },
                        onLongClick = {
                            Toast.makeText(context, "${tool.title}: ${tool.subtitle}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernToolButton(
    tool: UnifiedTool,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(20.dp),
            color = tool.containerColor,
            border = BorderStroke(1.2.dp, tool.borderColor)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(tool.iconRes),
                    contentDescription = tool.title,
                    tint = tool.iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = tool.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF334155),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}