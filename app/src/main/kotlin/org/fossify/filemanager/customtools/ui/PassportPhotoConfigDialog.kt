package org.fossify.filemanager.customtools.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.filemanager.customtools.layout.PassportPhotoLayoutEngine
import org.fossify.filemanager.customtools.passport.PassportBackground
import org.fossify.filemanager.customtools.passport.PassportPhotoProcessor
import org.fossify.filemanager.customtools.preview.ResultDialog
import java.io.File
import java.io.FileOutputStream

object PassportPhotoConfigDialog {

    fun show(
        context: Context,
        imagePath: String
    ) {

        val activity =
            context as? FragmentActivity
                ?: return

        if (
            activity.isFinishing ||
            activity.isDestroyed
        ) {
            return
        }

        val dialog =
            Dialog(context)

        var selectedRows =
            1

        var selectedBg =
            PassportBackground.SKY_BLUE

        val root =
            LinearLayout(context).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(context, 20),
                    dp(context, 20),
                    dp(context, 20),
                    dp(context, 20)
                )

                background =
                    roundedBackground(
                        Color.WHITE,
                        dp(context, 24)
                    )
            }

        // ---------------------------------------------------------
        // TITLE
        // ---------------------------------------------------------

        root.addView(
            TextView(context).apply {

                text =
                    "Passport Photo"

                textSize =
                    22f

                setTextColor(
                    Color.BLACK
                )
            }
        )

        root.addView(
            TextView(context).apply {

                text =
                    "30 × 40 mm  •  6 photos per row"

                textSize =
                    13f

                setTextColor(
                    Color.GRAY
                )

                setPadding(
                    0,
                    dp(context, 4),
                    0,
                    dp(context, 14)
                )
            }
        )

        // ---------------------------------------------------------
        // ROW COUNT
        // ---------------------------------------------------------

        root.addView(
            TextView(context).apply {

                text =
                    "Number of rows"

                textSize =
                    15f

                setTextColor(
                    Color.DKGRAY
                )
            }
        )

        val rowContainer =
            LinearLayout(context).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
            }

        val rowButtons =
            ArrayList<TextView>()

        fun updateRowButtons() {

            rowButtons.forEachIndexed {
                index,
                button ->

                val selected =
                    index + 1 ==
                        selectedRows

                button.setTextColor(
                    if (selected)
                        Color.WHITE
                    else
                        Color.DKGRAY
                )

                button.background =
                    roundedBackground(
                        if (selected)
                            Color.rgb(
                                55,
                                95,
                                220
                            )
                        else
                            Color.rgb(
                                240,
                                240,
                                240
                            ),
                        dp(context, 10)
                    )
            }
        }

        for (row in 1..6) {

            val rowButton =
                TextView(context).apply {

                    text =
                        row.toString()

                    textSize =
                        16f

                    gravity =
                        Gravity.CENTER

                    setOnClickListener {

                        selectedRows =
                            row

                        updateRowButtons()
                    }
                }

            rowButtons.add(
                rowButton
            )

            rowContainer.addView(
                rowButton,
                LinearLayout.LayoutParams(
                    0,
                    dp(context, 44),
                    1f
                ).apply {

                    setMargins(
                        dp(context, 3),
                        dp(context, 8),
                        dp(context, 3),
                        dp(context, 10)
                    )
                }
            )
        }

        root.addView(
            rowContainer
        )

        updateRowButtons()

        // ---------------------------------------------------------
        // BACKGROUND
        // ---------------------------------------------------------

        root.addView(
            TextView(context).apply {

                text =
                    "Background"

                textSize =
                    15f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    dp(context, 4),
                    0,
                    dp(context, 5)
                )
            }
        )

        val backgroundScroll =
            HorizontalScrollView(context).apply {

                isHorizontalScrollBarEnabled =
                    false
            }

        val backgroundContainer =
            LinearLayout(context).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        val backgroundButtons =
            ArrayList<TextView>()

        fun updateBackgroundButtons() {

            backgroundButtons.forEachIndexed {
                index,
                button ->

                val item =
                    PassportBackground
                        .entries[index]

                val selected =
                    item ==
                        selectedBg

                button.background =
                    roundedBackground(
                        if (selected)
                            Color.rgb(
                                220,
                                235,
                                255
                            )
                        else
                            Color.rgb(
                                245,
                                245,
                                245
                            ),
                        dp(context, 10)
                    )
            }
        }

        PassportBackground.entries.forEach {
            bgOption ->

            val bgButton =
                TextView(context).apply {

                    text =
                        bgOption.title

                    textSize =
                        13f

                    gravity =
                        Gravity.CENTER

                    setTextColor(
                        Color.DKGRAY
                    )

                    setOnClickListener {

                        selectedBg =
                            bgOption

                        updateBackgroundButtons()
                    }
                }

            backgroundButtons.add(
                bgButton
            )

            backgroundContainer.addView(
                bgButton,
                LinearLayout.LayoutParams(
                    dp(context, 105),
                    dp(context, 46)
                ).apply {

                    setMargins(
                        dp(context, 3),
                        dp(context, 3),
                        dp(context, 3),
                        dp(context, 8)
                    )
                }
            )
        }

        backgroundScroll.addView(
            backgroundContainer
        )

        root.addView(
            backgroundScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 58)
            )
        )

        updateBackgroundButtons()

        // ---------------------------------------------------------
        // INFO
        // ---------------------------------------------------------

        root.addView(
            TextView(context).apply {

                text =
                    "Crop → remove background → apply background → generate"

                textSize =
                    12f

                setTextColor(
                    Color.GRAY
                )

                setPadding(
                    0,
                    dp(context, 5),
                    0,
                    dp(context, 5)
                )
            }
        )

        // ---------------------------------------------------------
        // BUTTONS
        // ---------------------------------------------------------

        val buttonRow =
            LinearLayout(context).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.END
            }

        val cancelButton =
            Button(context).apply {

                text =
                    "Cancel"

                setOnClickListener {

                    dialog.dismiss()
                }
            }

        val createButton =
            Button(context)

        createButton.text =
            "Generate"

        createButton.setOnClickListener {

            createButton.isEnabled =
                false

            createButton.text =
                "Processing…"

            activity.lifecycleScope.launch {

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {

                        generateSheet(
                            context = context,
                            imagePath = imagePath,
                            rows = selectedRows,
                            background = selectedBg
                        )
                    }

                createButton.isEnabled =
                    true

                createButton.text =
                    "Generate"

                if (result == null) {

                    Toast.makeText(
                        context,
                        "Unable to create passport sheet",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                dialog.dismiss()

                ResultDialog.show(
                    context,
                    result.absolutePath,
                    "Passport Photos"
                )
            }
        }

        buttonRow.addView(
            cancelButton
        )

        buttonRow.addView(
            createButton
        )

        root.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 56)
            ).apply {

                topMargin =
                    dp(context, 6)
            }
        )

        dialog.setContentView(
            root
        )

        dialog.window?.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        dialog.setCanceledOnTouchOutside(
            true
        )

        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.window?.setGravity(
            Gravity.BOTTOM
        )
    }

    private suspend fun generateSheet(
        context: Context,
        imagePath: String,
        rows: Int,
        background: PassportBackground
    ): File? {

        val source =
            BitmapFactory.decodeFile(
                imagePath
            ) ?: return null

        var foreground: Bitmap? =
            null

        var processed: Bitmap? =
            null

        var sheet: Bitmap? =
            null

        return try {

            foreground =
                PassportPhotoProcessor
                    .removeBackground(
                        source
                    )

            if (foreground == null) {
                return null
            }

            processed =
                PassportPhotoProcessor
                    .applyBackground(
                        foreground,
                        background
                    )

            val rowBitmaps =
                mutableMapOf<Int, Bitmap>()

            val selectedRowIndex =
                (rows - 1).coerceIn(0, 5)

            rowBitmaps[selectedRowIndex] =
                processed

            sheet =
                PassportPhotoLayoutEngine
                    .createA4(
                        rowBitmaps
                    )

            val finalSheet = sheet ?: return null

            val directory =
                File(
                    context.cacheDir,
                    "custom_tools"
                )

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val output =
                File(
                    directory,
                    "passport_${System.currentTimeMillis()}.png"
                )

            FileOutputStream(
                output
            ).use { stream ->

                finalSheet.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    stream
                )
            }

            output

        } catch (_: Exception) {

            null

        } finally {

            source.recycle()

            foreground?.recycle()

            processed?.recycle()

            sheet?.recycle()
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius =
                radius.toFloat()
        }
    }

    private fun dp(
        context: Context,
        value: Int
    ): Int {

        return (
            value *
                context.resources
                    .displayMetrics
                    .density
            ).toInt()
    }
}


