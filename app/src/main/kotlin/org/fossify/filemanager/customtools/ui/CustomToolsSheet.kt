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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.yalantis.ucrop.UCrop
import org.fossify.filemanager.R
import org.fossify.filemanager.customtools.CustomToolRunner
import org.fossify.filemanager.customtools.idcard.IdCardActivity
import org.fossify.filemanager.customtools.passport.PassportA4SheetActivity
import org.fossify.filemanager.customtools.preview.ResultDialog
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
                    ResultDialog.show(
                        requireContext(),
                        path,
                        "Cropped Image"
                    )
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR && result.data != null) {
                val cropError = UCrop.getError(result.data!!)
                Toast.makeText(
                    requireContext(),
                    cropError?.message ?: "Crop failed",
                    Toast.LENGTH_SHORT
                ).show()
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
            window.setDimAmount(0.55f)
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun handleToolClick(toolId: ToolId) {
        if (imagePath.isBlank()) return

        when (toolId) {
            ToolId.ID_CARD -> {
                dismiss()
                val intent = Intent(requireContext(), IdCardActivity::class.java).apply {
                    putStringArrayListExtra(IdCardActivity.EXTRA_IMAGE_PATHS, ArrayList(imagePaths))
                }
                requireContext().startActivity(intent)
            }
            ToolId.PASSPORT_PHOTO -> {
                dismiss()
                val intent = Intent(requireContext(), PassportA4SheetActivity::class.java).apply {
                    putStringArrayListExtra(PassportA4SheetActivity.EXTRA_IMAGE_PATHS, ArrayList(imagePaths))
                }
                requireContext().startActivity(intent)
            }
            ToolId.JUGANUA -> {
                if (JuganuaHelper.open(requireContext(), imagePath)) dismiss()
            }
            ToolId.COMPRESS_IMAGE -> {
                dismiss()
                ImageCompressionDialog.show(requireContext(), imagePath)
            }
            ToolId.CROP_IMAGE -> {
                val sourceUri = Uri.fromFile(File(imagePath))
                val destinationUri = Uri.fromFile(
                    File(requireContext().cacheDir, "crop_${System.currentTimeMillis()}.png")
                )
                val uCropIntent = UCrop.of(sourceUri, destinationUri)
                    .withOptions(UCrop.Options().apply { setFreeStyleCropEnabled(true) })
                    .getIntent(requireContext())
                cropImageLauncher.launch(uCropIntent)
            }
            ToolId.CONVERT_PDF -> {
                val result = CustomToolRunner.convertPdf(requireContext(), imagePath)
                if (result.success && result.outputPath != null) {
                    dismiss()
                    ResultDialog.show(requireContext(), result.outputPath, "PDF")
                }
            }
            ToolId.PDF_UNLOCKER -> {
                dismiss()
                val uri = Uri.fromFile(File(imagePath))
                org.fossify.filemanager.customtools.pdfunlocker.PdfUnlockerDialog.show(requireContext(), uri)
            }
            ToolId.WHATSAPP -> {
                if (WhatsAppHelper.share(requireContext(), imagePath)) dismiss()
            }
            ToolId.NOKOPRINT -> {
                if (NokoPrintHelper.print(requireContext(), imagePath)) dismiss()
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
    val containerColor: Color
)

@Composable
private fun CustomToolsContent(
    onDismiss: () -> Unit,
    onToolClick: (ToolId) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val allTools = listOf(
        UnifiedTool(ToolId.WHATSAPP, "WhatsApp", "Share directly via WhatsApp", R.drawable.ic_tool_whatsapp, Color(0xFF25D366), Color(0xFF25D366).copy(alpha = 0.12f)),
        UnifiedTool(ToolId.NOKOPRINT, "NokoPrint", "Print documents or photos", R.drawable.ic_tool_print, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
        UnifiedTool(ToolId.CROP_IMAGE, "Crop Image", "Adjust image boundaries", R.drawable.ic_tool_crop, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
        UnifiedTool(ToolId.ID_CARD, "ID Card", "85.6 × 54 mm layout on A4", R.drawable.ic_tool_id_card, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondaryContainer),
        UnifiedTool(ToolId.PASSPORT_PHOTO, "Passport Photo", "30 × 40 mm • 36 slots (A4)", R.drawable.ic_tool_passport, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondaryContainer),
        UnifiedTool(ToolId.JUGANUA, "Juganua", "Open image inside Juganua", R.drawable.ic_tool_juganua, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondaryContainer),
        UnifiedTool(ToolId.COMPRESS_IMAGE, "Compress", "Reduce file size efficiently", R.drawable.ic_tool_compress, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondaryContainer),
        UnifiedTool(ToolId.CONVERT_PDF, "To PDF", "Convert image to PDF document", R.drawable.ic_tool_pdf, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondaryContainer),
        UnifiedTool(ToolId.PDF_UNLOCKER, "Unlocker", "Remove PDF password / Brute-force", R.drawable.ic_tool_pdf, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondaryContainer)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Custom Tools",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Long press any tool for info",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onDismiss() }
                        .padding(9.dp)
                )
            }

            // Grid of circular tool buttons with names below
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = false
            ) {
                items(allTools) { tool ->
                    CircularToolButtonWithLabel(
                        tool = tool,
                        onClick = { onToolClick(tool.id) },
                        onLongClick = {
                            Toast.makeText(context, "${tool.title}: ${tool.subtitle}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CircularToolButtonWithLabel(
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
                .size(56.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = CircleShape,
            color = tool.containerColor,
            tonalElevation = 2.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(tool.iconRes),
                    contentDescription = tool.title,
                    tint = tool.iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = tool.title,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}