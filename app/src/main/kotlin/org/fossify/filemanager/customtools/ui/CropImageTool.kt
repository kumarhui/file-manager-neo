package org.fossify.filemanager.customtools.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import org.fossify.filemanager.customtools.preview.ResultDialog
import org.fossify.filemanager.customtools.ui.PassportPhotoConfigDialog
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

private const val ARG_IMAGE_PATH = "image_path"
private const val ARG_CONTINUE_TO_PASSPORT = "continue_to_passport"
private const val TAG = "CropImageDialog"

object CropImageTool {

    fun show(
        context: Context,
        imagePath: String,
        continueToPassport: Boolean = false
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

        val manager =
            activity.supportFragmentManager

        if (manager.isStateSaved) {
            return
        }

        CropImageDialogFragment().apply {

            arguments = Bundle().apply {

                putString(
                    ARG_IMAGE_PATH,
                    imagePath
                )

                putBoolean(
                    ARG_CONTINUE_TO_PASSPORT,
                    continueToPassport
                )
            }

        }.show(
            manager,
            TAG
        )
    }
}


 class CropImageDialogFragment :
    DialogFragment() {

    private var imagePath = ""

    private var continueToPassport = false

    private lateinit var imageView: ImageView
    private lateinit var cropView: CropOverlayView

    private var sourceBitmap: Bitmap? = null

    companion object {

        private const val ARG_IMAGE_PATH =
            "image_path"

        private const val ARG_CONTINUE_TO_PASSPORT =
            "continue_to_passport"

        private const val TAG =
            "CropImageDialog"

        /*
         * Approximate initial crop size.
         *
         * 35 mm is deliberately used as the
         * starting physical size, which is
         * between the requested 30–40 mm range.
         */
        private const val INITIAL_CROP_MM = 35f
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        imagePath =
            arguments
                ?.getString(
                    ARG_IMAGE_PATH
                )
                .orEmpty()

        continueToPassport =
            arguments
                ?.getBoolean(
                    ARG_CONTINUE_TO_PASSPORT,
                    false
                )
                ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val context =
            requireContext()

        val root =
            FrameLayout(context)

        root.setPadding(
            dp(16),
            dp(16),
            dp(16),
            dp(12)
        )

        /*
         * Image
         */
        imageView =
            ImageView(context).apply {

                scaleType =
                    ImageView.ScaleType.FIT_CENTER

                setBackgroundColor(
                    android.graphics.Color.BLACK
                )
            }

        root.addView(
            imageView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(430)
            ).apply {
                gravity = Gravity.TOP
            }
        )

        /*
         * Crop overlay.
         */
        cropView =
            CropOverlayView(context).apply {

                setInitialCropSize(
                    INITIAL_CROP_MM
                )

                onCropChanged = {
                    updateCropPosition()
                }
            }

        root.addView(
            cropView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(430)
            ).apply {
                gravity = Gravity.TOP
            }
        )

        /*
         * Bottom buttons.
         */
        val buttons =
            android.widget.LinearLayout(context).apply {

                orientation =
                    android.widget.LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val cancel =
            Button(context).apply {

                text = "Cancel"

                setOnClickListener {
                    dismiss()
                }
            }

        buttons.addView(
            cancel,
            android.widget.LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            ).apply {
                setMargins(
                    0,
                    dp(12),
                    dp(6),
                    0
                )
            }
        )

        val action =
            Button(context).apply {

                text =
                    if (continueToPassport) {
                        "Next"
                    } else {
                        "Done"
                    }

                setOnClickListener {
                    cropImage()
                }
            }

        buttons.addView(
            action,
            android.widget.LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            ).apply {
                setMargins(
                    dp(6),
                    dp(12),
                    0,
                    0
                )
            }
        )

        root.addView(
            buttons,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
            ).apply {
                gravity = Gravity.BOTTOM
            }
        )

        loadBitmap()

        return root
    }

    private fun loadBitmap() {

        if (imagePath.isBlank()) {
            return
        }

        val bitmap =
            BitmapFactory.decodeFile(
                imagePath
            )

        if (bitmap == null) {

            Toast.makeText(
                requireContext(),
                "Unable to open image",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        sourceBitmap =
            bitmap

        imageView.setImageBitmap(
            bitmap
        )

        imageView.post {

            cropView.setImageBounds(
                imageView.width,
                imageView.height
            )

            cropView.setInitialCropSize(
                INITIAL_CROP_MM
            )
        }
    }

    private fun updateCropPosition() {
        // The overlay handles its own drawing.
    }

    private fun cropImage() {

        val bitmap =
            sourceBitmap
                ?: return

        try {

            val cropRect =
                cropView.getCropRect()

            if (cropRect.width() <= 1f ||
                cropRect.height() <= 1f
            ) {
                Toast.makeText(
                    requireContext(),
                    "Please select a crop area",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            /*
             * ImageView uses FIT_CENTER.
             *
             * Find the actual displayed image
             * rectangle inside the ImageView.
             */
            val imageWidth =
                bitmap.width.toFloat()

            val imageHeight =
                bitmap.height.toFloat()

            val viewWidth =
                imageView.width.toFloat()

            val viewHeight =
                imageView.height.toFloat()

            val scale =
                min(
                    viewWidth / imageWidth,
                    viewHeight / imageHeight
                )

            val displayedWidth =
                imageWidth * scale

            val displayedHeight =
                imageHeight * scale

            val imageLeft =
                (viewWidth - displayedWidth) / 2f

            val imageTop =
                (viewHeight - displayedHeight) / 2f

            val left =
                ((cropRect.left - imageLeft) / scale)
                    .toInt()
                    .coerceIn(
                        0,
                        bitmap.width - 1
                    )

            val top =
                ((cropRect.top - imageTop) / scale)
                    .toInt()
                    .coerceIn(
                        0,
                        bitmap.height - 1
                    )

            val right =
                ((cropRect.right - imageLeft) / scale)
                    .toInt()
                    .coerceIn(
                        left + 1,
                        bitmap.width
                    )

            val bottom =
                ((cropRect.bottom - imageTop) / scale)
                    .toInt()
                    .coerceIn(
                        top + 1,
                        bitmap.height
                    )

            val width =
                max(
                    1,
                    right - left
                )

            val height =
                max(
                    1,
                    bottom - top
                )

            val cropped =
                Bitmap.createBitmap(
                    bitmap,
                    left,
                    top,
                    width,
                    height
                )

            val outputFile =
                File(
                    requireContext().cacheDir,
                    "custom_tools"
                ).also {
                    if (!it.exists()) {
                        it.mkdirs()
                    }
                }

            val file =
                File(
                    outputFile,
                    "cropped_${System.currentTimeMillis()}.jpg"
                )

            FileOutputStream(file).use { stream ->

                cropped.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    stream
                )
            }

            cropped.recycle()

            /*
             * IMPORTANT:
             *
             * Passport mode:
             *
             * Crop → Next → PassportPhotoConfigDialog
             *
             * Normal mode:
             *
             * Crop → Done → ResultDialog
             */
            if (continueToPassport) {

                dismiss()

                PassportPhotoConfigDialog.show(
                    requireContext(),
                    file.absolutePath
                )

            } else {

                dismiss()

                ResultDialog.show(
                    requireContext(),
                    file.absolutePath,
                    "Cropped Image"
                )
            }

        } catch (e: Exception) {

            Toast.makeText(
                requireContext(),
                "Crop failed: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {

        imageView.setImageDrawable(
            null
        )

        super.onDestroyView()
    }

    override fun onDestroy() {

        sourceBitmap?.let {

            if (!it.isRecycled) {
                it.recycle()
            }
        }

        sourceBitmap = null

        super.onDestroy()
    }

    override fun onStart() {

        super.onStart()

        dialog?.window?.let { window ->

            window.setBackgroundDrawableResource(
                android.R.color.transparent
            )

            window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            window.setDimAmount(
                0.55f
            )

            window.setGravity(
                Gravity.CENTER
            )

            window.setLayout(
                (resources.displayMetrics.widthPixels * 0.96f)
                    .toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }
}


/**
 * Simple crop overlay.
 *
 * This intentionally does not depend on
 * the old Cropper "Guidelines" API.
 */
private class CropOverlayView(
    context: Context
) : View(context) {

    private val paint =
        android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG
        )

    private val cropRect =
        RectF()

    private var imageWidth =
        0

    private var imageHeight =
        0

    private var initialSizeSet =
        false

    private var dragging =
        false

    private var resizing =
        false

    private var lastX =
        0f

    private var lastY =
        0f

    var onCropChanged:
        (() -> Unit)? = null

    init {

        paint.style =
            android.graphics.Paint.Style.STROKE

        paint.strokeWidth =
            dp(2).toFloat()

        paint.color =
            android.graphics.Color.WHITE
    }

    fun setImageBounds(
        width: Int,
        height: Int
    ) {

        imageWidth =
            width

        imageHeight =
            height

        if (!initialSizeSet) {

            post {
                createInitialCrop()
            }
        }
    }

    fun setInitialCropSize(
        mm: Float
    ) {

        initialSizeSet = false

        post {
            createInitialCrop()
        }
    }

    private fun createInitialCrop() {

        if (width <= 0 || height <= 0) {
            return
        }

        /*
         * Physical ratio:
         *
         * 30 mm width
         * 40 mm height
         *
         * Therefore:
         *
         * width : height = 3 : 4
         */
        val maxWidth =
            min(
                width * 0.55f,
                height * 0.45f
            )

        val cropWidth =
            maxWidth

        val cropHeight =
            cropWidth * 40f / 30f

        /*
         * Center the 30 × 40 proportion
         * on the image.
         */
        val left =
            (width - cropWidth) / 2f

        val top =
            (height - cropHeight) / 2f

        cropRect.set(
            left,
            top,
            left + cropWidth,
            top + cropHeight
        )

        initialSizeSet = true

        invalidate()

        onCropChanged?.invoke()
    }

    fun getCropRect(): RectF {

        return RectF(
            cropRect
        )
    }

    override fun onDraw(
        canvas: android.graphics.Canvas
    ) {

        super.onDraw(canvas)

        /*
         * Darken outside the crop area.
         */
        val overlayPaint =
            android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    android.graphics.Color.argb(
                        110,
                        0,
                        0,
                        0
                    )

                style =
                    android.graphics.Paint.Style.FILL
            }

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            overlayPaint
        )

        /*
         * Clear crop area.
         */
        val clearPaint =
            android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
            ).apply {

                xfermode =
                    android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.CLEAR
                    )
            }

        val save =
            canvas.saveLayer(
                null,
                null
            )

        canvas.drawRect(
            cropRect,
            clearPaint
        )

        canvas.restoreToCount(
            save
        )

        /*
         * Border.
         */
        paint.color =
            android.graphics.Color.WHITE

        paint.strokeWidth =
            dp(2).toFloat()

        canvas.drawRect(
            cropRect,
            paint
        )

        /*
         * Rule-of-thirds guide.
         */
        paint.color =
            android.graphics.Color.argb(
                150,
                255,
                255,
                255
            )

        paint.strokeWidth =
            dp(1).toFloat()

        val thirdX =
            cropRect.width() / 3f

        val thirdY =
            cropRect.height() / 3f

        canvas.drawLine(
            cropRect.left + thirdX,
            cropRect.top,
            cropRect.left + thirdX,
            cropRect.bottom,
            paint
        )

        canvas.drawLine(
            cropRect.left + thirdX * 2,
            cropRect.top,
            cropRect.left + thirdX * 2,
            cropRect.bottom,
            paint
        )

        canvas.drawLine(
            cropRect.left,
            cropRect.top + thirdY,
            cropRect.right,
            cropRect.top + thirdY,
            paint
        )

        canvas.drawLine(
            cropRect.left,
            cropRect.top + thirdY * 2,
            cropRect.right,
            cropRect.top + thirdY * 2,
            paint
        )

        /*
         * Resize handle.
         */
        paint.color =
            android.graphics.Color.WHITE

        paint.style =
            android.graphics.Paint.Style.FILL

        canvas.drawCircle(
            cropRect.right,
            cropRect.bottom,
            dp(8).toFloat(),
            paint
        )

        paint.style =
            android.graphics.Paint.Style.STROKE
    }

    override fun onTouchEvent(
        event: android.view.MotionEvent
    ): Boolean {

        when (event.actionMasked) {

            android.view.MotionEvent.ACTION_DOWN -> {

                lastX =
                    event.x

                lastY =
                    event.y

                val handleRadius =
                    dp(28).toFloat()

                resizing =
                    distance(
                        event.x,
                        event.y,
                        cropRect.right,
                        cropRect.bottom
                    ) <= handleRadius

                dragging =
                    cropRect.contains(
                        event.x,
                        event.y
                    ) && !resizing

                return true
            }

            android.view.MotionEvent.ACTION_MOVE -> {

                val dx =
                    event.x - lastX

                val dy =
                    event.y - lastY

                if (resizing) {

                    resizeCrop(
                        dx,
                        dy
                    )

                } else if (dragging) {

                    moveCrop(
                        dx,
                        dy
                    )
                }

                lastX =
                    event.x

                lastY =
                    event.y

                invalidate()

                onCropChanged?.invoke()

                return true
            }

            android.view.MotionEvent.ACTION_UP -> {

                val wasClick =
                    dragging || resizing

                dragging = false
                resizing = false

                if (wasClick) {
                    performClick()
                }

                return true
            }

            android.view.MotionEvent.ACTION_CANCEL -> {

                dragging = false
                resizing = false

                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun moveCrop(
        dx: Float,
        dy: Float
    ) {

        var newLeft =
            cropRect.left + dx

        var newTop =
            cropRect.top + dy

        val width =
            cropRect.width()

        val height =
            cropRect.height()

        newLeft =
            newLeft.coerceIn(
                0f,
                this.width - width
            )

        newTop =
            newTop.coerceIn(
                0f,
                this.height - height
            )

        cropRect.set(
            newLeft,
            newTop,
            newLeft + width,
            newTop + height
        )
    }

    private fun resizeCrop(
        dx: Float,
        dy: Float
    ) {

        /*
         * Maintain 30:40 = 3:4 portrait ratio.
         */
        val delta =
            max(
                dx,
                dy
            )

        val minimumWidth =
            dp(70).toFloat()

        val maximumWidth =
            min(
                width * 0.90f,
                height * 0.675f
            )

        val newWidth =
            (
                cropRect.width() + delta
                ).coerceIn(
                    minimumWidth,
                    maximumWidth
                )

        val newHeight =
            newWidth * 40f / 30f

        /*
         * Keep the top-left corner fixed
         * while dragging the bottom-right handle.
         */
        var left =
            cropRect.left

        var top =
            cropRect.top

        var right =
            left + newWidth

        var bottom =
            top + newHeight

        /*
         * Keep rectangle inside the view.
         */
        if (right > width) {

            right =
                width.toFloat()

            left =
                right - newWidth
        }

        if (bottom > height) {

            bottom =
                height.toFloat()

            top =
                bottom - newHeight
        }

        if (left < 0f) {

            left = 0f
            right = newWidth
        }

        if (top < 0f) {

            top = 0f
            bottom = newHeight
        }

        cropRect.set(
            left,
            top,
            right,
            bottom
        )
    }
    private fun distance(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {

        val dx =
            x1 - x2

        val dy =
            y1 - y2

        return kotlin.math.sqrt(
            dx * dx + dy * dy
        )
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }
}
