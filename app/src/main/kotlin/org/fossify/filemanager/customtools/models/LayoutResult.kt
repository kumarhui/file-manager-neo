package org.fossify.filemanager.customtools.models

data class LayoutResult(
    val success: Boolean,
    val outputPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val error: String? = null
)
