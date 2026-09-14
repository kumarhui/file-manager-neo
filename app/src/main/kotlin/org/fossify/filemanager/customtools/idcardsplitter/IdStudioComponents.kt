package org.fossify.filemanager.customtools.idcardsplitter

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SlotSliderSelector(
    positions: List<PrintPosition>,
    selectedPosition: PrintPosition,
    slots: Map<PrintPosition, SlotData>,
    onPositionChanged: (PrintPosition) -> Unit,
    onAddOrReplace: () -> Unit,
    onCropSlot: () -> Unit
) {
    val currentIndex = positions.indexOf(selectedPosition).coerceAtLeast(0)
    val hasCard = slots.containsKey(selectedPosition)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ROW POSITION SELECTOR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.Gray
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasCard) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (hasCard) Color(0xFF86EFAC) else Color(0xFFCBD5E1))
                ) {
                    Text(
                        text = if (hasCard) "● FILLED" else "○ BLANK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasCard) Color(0xFF15803D) else Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { if (currentIndex > 0) onPositionChanged(positions[currentIndex - 1]) },
                    enabled = currentIndex > 0,
                    modifier = Modifier.size(34.dp).background(Color.White, CircleShape).border(1.dp, Color(0xFFE2E8F0), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Slot", modifier = Modifier.size(16.dp))
                }

                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { floatVal ->
                        val targetIdx = floatVal.toInt().coerceIn(0, positions.size - 1)
                        onPositionChanged(positions[targetIdx])
                    },
                    valueRange = 0f..(positions.size - 1).toFloat(),
                    steps = if (positions.size > 2) positions.size - 2 else 0,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                IconButton(
                    onClick = { if (currentIndex < positions.size - 1) onPositionChanged(positions[currentIndex + 1]) },
                    enabled = currentIndex < positions.size - 1,
                    modifier = Modifier.size(34.dp).background(Color.White, CircleShape).border(1.dp, Color(0xFFE2E8F0), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Slot", modifier = Modifier.size(16.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Row: ${currentIndex + 1} of ${positions.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasCard) {
                        FilledTonalIconButton(
                            onClick = onCropSlot,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = "Crop Card", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Button(
                        onClick = onAddOrReplace,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(if (hasCard) Icons.Default.Sync else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (hasCard) "Replace" else "Add Card", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundOptionRow(
    currentBg: PageBackground,
    onSelectWhite: () -> Unit,
    onSelectGallery: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Page Background", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onSelectWhite,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                border = BorderStroke(1.dp, if (currentBg is PageBackground.White) MaterialTheme.colorScheme.primary else Color.LightGray)
            ) {
                Text("White", fontSize = 10.sp)
            }

            Button(
                onClick = onSelectGallery,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (currentBg is PageBackground.CustomImage) "Custom Bg" else "Gallery Bg", fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun FinalPreviewCard(
    bitmap: Bitmap?,
    isGenerating: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFBFD)),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "A4 LIVE PREVIEW",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.DarkGray
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = onSave,
                        enabled = bitmap != null && !isGenerating,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Save image", modifier = Modifier.size(18.dp))
                    }

                    FilledTonalIconButton(
                        onClick = onShare,
                        enabled = bitmap != null && !isGenerating,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share image", modifier = Modifier.size(18.dp))
                    }

                    FilledIconButton(
                        onClick = onPrint,
                        enabled = bitmap != null && !isGenerating,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print page", modifier = Modifier.size(18.dp), tint = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .aspectRatio(1f / 1.4142f)
                    .shadow(4.dp, RoundedCornerShape(3.dp))
                    .background(Color.White)
                    .border(0.8.dp, Color(0xFFCBD5E1), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                } else if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Layers, null, Modifier.size(32.dp), Color.LightGray)
                        Spacer(Modifier.height(4.dp))
                        Text("Add card using slider to generate preview", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun ResultItem(modifier: Modifier, label: String, bitmap: Bitmap?) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
        Spacer(Modifier.height(6.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.58f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                bitmap?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize().padding(8.dp)) }
            }
        }
    }
}

@Composable
fun FlipControls(onHorizontalFlip: () -> Unit, onVerticalFlip: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = onHorizontalFlip, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(36.dp)) {
            Icon(Icons.Default.Flip, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onVerticalFlip, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(36.dp)) {
            Icon(Icons.Default.Flip, null, modifier = Modifier.size(18.dp).graphicsLayer(rotationZ = 90f), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PreviewCard(bitmap: Bitmap?, loading: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.58f),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator()
            bitmap?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit) }
        }
    }
}