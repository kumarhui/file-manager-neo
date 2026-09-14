package org.fossify.filemanager.customtools.idcardsplitter

import android.graphics.Bitmap

enum class FlowState {
    PAGE_OVERVIEW, PREVIEW_READY, CROPPED, SLICED
}

enum class PaperSize { A4, A6 }

enum class PrintPosition {
    POS_1, POS_2, POS_3, POS_4, POS_5, POS_6
}

data class SlotData(
    val front: Bitmap,
    val back: Bitmap
)

sealed class PageBackground {
    data object White : PageBackground()
    data class SolidColor(val colorInt: Int) : PageBackground()
    data class CustomImage(val bitmap: Bitmap) : PageBackground()
}