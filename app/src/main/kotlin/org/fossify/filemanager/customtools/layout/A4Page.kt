package org.fossify.filemanager.customtools.layout

object A4Page {

    const val WIDTH_MM = 210f
    const val HEIGHT_MM = 297f

    const val WIDTH_PX = 1240
    const val HEIGHT_PX = 1754

    fun mmToPx(mm: Float): Float =
        WIDTH_PX * mm / WIDTH_MM
}
