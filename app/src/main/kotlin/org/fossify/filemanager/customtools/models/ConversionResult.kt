package org.fossify.filemanager.customtools.models

data class ConversionResult(
    val success: Boolean,
    val outputPath: String? = null,
    val error: String? = null
)
