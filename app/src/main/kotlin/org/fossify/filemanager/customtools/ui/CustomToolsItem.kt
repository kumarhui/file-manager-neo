package org.fossify.filemanager.customtools.ui

import androidx.annotation.DrawableRes

data class CustomToolsItem(
    val id: ToolId,
    val title: String,
    val subtitle: String,
    @DrawableRes val iconRes: Int
)

enum class ToolId {
    ID_CARD,
    PASSPORT_PHOTO,
    JUGANUA,
    COMPRESS_IMAGE,
    CROP_IMAGE,
    CONVERT_PDF,
    PDF_UNLOCKER,
    WHATSAPP,
    NOKOPRINT
}