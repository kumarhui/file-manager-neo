package org.fossify.filemanager.customtools.idcardsplitter

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import java.io.File

class IdCardSplitterActivity : ComponentActivity() {

    companion object {
        const val EXTRA_IMAGE_PATHS = "extra_image_paths"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val paths = intent.getStringArrayListExtra(EXTRA_IMAGE_PATHS) ?: arrayListOf()
        val uris = paths.map { Uri.fromFile(File(it)) }

        setContent {
            MaterialTheme {
                ExtractIdCardScreen(
                    initialUri = uris.firstOrNull(),
                    initialUris = uris.ifEmpty { null },
                    onBack = { finish() }
                )
            }
        }
    }
}