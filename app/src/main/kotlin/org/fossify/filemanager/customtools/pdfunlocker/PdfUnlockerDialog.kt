package org.fossify.filemanager.customtools.pdfunlocker

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity

class PdfUnlockerDialog : DialogFragment() {

    companion object {

        private const val TAG = "PdfUnlockerDialog"
        private const val ARG_URI = "uri"

        fun show(
            context: Context,
            uri: Uri? = null
        ) {
            val activity = context.findFragmentActivity() ?: return

            if (
                activity.isFinishing ||
                activity.isDestroyed
            ) {
                return
            }

            val manager =
                activity.supportFragmentManager

            if (
                manager.isStateSaved ||
                manager.findFragmentByTag(TAG) != null
            ) {
                return
            }

            PdfUnlockerDialog().apply {

                arguments = Bundle().apply {
                    uri?.let {
                        putString(ARG_URI, it.toString())
                    }
                }

            }.show(manager, TAG)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val initialUri =
            arguments
                ?.getString(ARG_URI)
                ?.let(Uri::parse)

        return ComposeView(requireContext()).apply {

            setContent {

                MaterialTheme  {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {

                        PdfUnlockerScreen(
                            initialUri = initialUri,
                            onBack = {
                                dismiss()
                            },
                            onNavigateToTool = { _, _ ->
                                // Keep cross-tool navigation disabled
                                // until Prism-specific routing is added.
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.let { window ->
            window.setBackgroundDrawableResource(
                android.R.color.transparent
            )

            window.setDimAmount(0.5f)

            window.setLayout(
                (resources.displayMetrics.widthPixels * 0.96f).toInt(),
                (resources.displayMetrics.heightPixels * 0.92f).toInt()
            )
        }
    }}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this

    while (current is android.content.ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }

    return current as? FragmentActivity
}
