package org.fossify.filemanager.customtools.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.filemanager.customtools.passport.PassportBackground
import org.fossify.filemanager.customtools.passport.PassportPhotoProcessor
import org.fossify.filemanager.customtools.preview.ResultDialog
import java.io.File
import java.io.FileOutputStream

object CropImageTool {

    fun show(
        context: Context,
        imagePath: String,
        continueToPassport: Boolean = false
    ) {
        val activity = context as? FragmentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val sourceUri = Uri.fromFile(File(imagePath))
        val destUri = Uri.fromFile(
            File(activity.cacheDir, "ucrop_${System.currentTimeMillis()}.png")
        )

        // Fixed/locked crop frame with zoom & pan on the image (matching Passport tool)
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(false) // Locked frame: zoom/pan image instead of resizing edges
            setStatusBarColor(android.graphics.Color.WHITE)
            setToolbarColor(android.graphics.Color.WHITE)
            setToolbarWidgetColor(android.graphics.Color.BLACK)
            setToolbarTitle("Crop Image")
        }

        val uCropIntent = UCrop.of(sourceUri, destUri)
            .withAspectRatio(30f, 40f) // Matches Passport photo 30x40 mm ratio (adjust if needed)
            .withOptions(options)
            .getIntent(activity)

        startUCropForResult(activity, uCropIntent, continueToPassport)
    }

    private fun startUCropForResult(
        activity: FragmentActivity,
        intent: Intent,
        continueToPassport: Boolean
    ) {
        val fm = activity.supportFragmentManager
        val tag = "UCropDispatcherFragment"
        var fragment = fm.findFragmentByTag(tag) as? UCropDispatcherFragment
        if (fragment == null) {
            fragment = UCropDispatcherFragment()
            fm.beginTransaction().add(fragment, tag).commitNow()
        }
        fragment.launchUCrop(intent, continueToPassport)
    }
}

class UCropDispatcherFragment : androidx.fragment.app.Fragment() {

    private var continueToPassportFlag = false

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val croppedUri = UCrop.getOutput(result.data!!)
            if (croppedUri != null) {
                val path = croppedUri.path ?: ""
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    CropBackgroundDialog.show(
                        parentFragmentManager,
                        path,
                        bitmap,
                        continueToPassportFlag
                    )
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR && result.data != null) {
            val error = UCrop.getError(result.data!!)
            Toast.makeText(requireContext(), error?.message ?: "Crop failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchUCrop(intent: Intent, continueToPassport: Boolean) {
        continueToPassportFlag = continueToPassport
        cropLauncher.launch(intent)
    }
}

class CropBackgroundDialog : DialogFragment() {

    private var imagePath: String = ""
    private var continueToPassport: Boolean = false
    private var baseBitmap: Bitmap? = null

    companion object {
        fun show(
            manager: androidx.fragment.app.FragmentManager,
            filePath: String,
            bitmap: Bitmap,
            continueToPassport: Boolean
        ) {
            val dialog = CropBackgroundDialog()
            dialog.imagePath = filePath
            dialog.baseBitmap = bitmap
            dialog.continueToPassport = continueToPassport
            dialog.show(manager, "CropBackgroundDialog")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        baseBitmap?.let { bmp ->
                            BackgroundChangeContent(
                                originalBitmap = bmp,
                                onConfirm = { finalBmp ->
                                    val outFile = File(imagePath)
                                    FileOutputStream(outFile).use { out ->
                                        finalBmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                    }
                                    dismiss()

                                    if (continueToPassport) {
                                        PassportPhotoConfigDialog.show(requireContext(), outFile.absolutePath)
                                    } else {
                                        ResultDialog.show(requireContext(), outFile.absolutePath, "Processed Photo")
                                    }
                                },
                                onCancel = {
                                    dismiss()
                                    if (continueToPassport) {
                                        PassportPhotoConfigDialog.show(requireContext(), imagePath)
                                    } else {
                                        ResultDialog.show(requireContext(), imagePath, "Cropped Photo")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}

@Composable
private fun BackgroundChangeContent(
    originalBitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var enableBgRemoval by remember { mutableStateOf(true) }
    var selectedBackground by remember { mutableStateOf(PassportBackground.SKY_BLUE) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(true) }

    LaunchedEffect(enableBgRemoval, selectedBackground) {
        isProcessing = true
        withContext(Dispatchers.Default) {
            if (enableBgRemoval) {
                val foreground = PassportPhotoProcessor.removeBackground(originalBitmap)
                processedBitmap = if (foreground != null) {
                    PassportPhotoProcessor.applyBackground(foreground, selectedBackground)
                } else {
                    originalBitmap
                }
            } else {
                processedBitmap = originalBitmap
            }
        }
        isProcessing = false
    }

    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Change Background", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        Box(
            modifier = Modifier
                .size(140.dp, 180.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            } else {
                processedBitmap?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Remove Background (ML Kit)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = enableBgRemoval,
                onCheckedChange = { enableBgRemoval = it }
            )
        }

        if (enableBgRemoval) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Keep Original")
            }

            Button(
                onClick = { onConfirm(processedBitmap ?: originalBitmap) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = !isProcessing
            ) {
                Text("Done")
            }
        }
    }
}