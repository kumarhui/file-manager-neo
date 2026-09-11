package org.fossify.filemanager.customtools.layout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

object IdCardLayoutEngine {

    // ISO/IEC 7810 ID-1 standard dimensions
    const val CARD_WIDTH_MM = 85.60f
    const val CARD_HEIGHT_MM = 53.98f

    /**
     * Creates an A4 sheet containing up to 2 ID card images (front & back).
     * front: Placed in the upper half.
     * back: Placed in the lower half (if provided).
     */
    fun createA4Sheet(
        front: Bitmap,
        back: Bitmap? = null
    ): Bitmap {
        val pageWidth = A4Page.WIDTH_PX
        val pageHeight = A4Page.HEIGHT_PX

        val result = Bitmap.createBitmap(
            pageWidth,
            pageHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val cardWidth = A4Page.mmToPx(CARD_WIDTH_MM)
        val cardHeight = A4Page.mmToPx(CARD_HEIGHT_MM)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.LTGRAY
        }

        val centerX = (pageWidth - cardWidth) / 2f

        if (back == null) {
            // Single card centered vertically
            val centerY = (pageHeight - cardHeight) / 2f
            val dest = RectF(centerX, centerY, centerX + cardWidth, centerY + cardHeight)
            canvas.drawBitmap(front, null, dest, paint)
            canvas.drawRect(dest, strokePaint)
        } else {
            // Front in upper section
            val topCardY = (pageHeight * 0.28f) - (cardHeight / 2f)
            val frontDest = RectF(centerX, topCardY, centerX + cardWidth, topCardY + cardHeight)
            canvas.drawBitmap(front, null, frontDest, paint)
            canvas.drawRect(frontDest, strokePaint)

            // Back in lower section
            val bottomCardY = (pageHeight * 0.72f) - (cardHeight / 2f)
            val backDest = RectF(centerX, bottomCardY, centerX + cardWidth, bottomCardY + cardHeight)
            canvas.drawBitmap(back, null, backDest, paint)
            canvas.drawRect(backDest, strokePaint)
        }

        return result
    }

    /**
     * Splits any list of images into pairs of 2 and generates an A4 sheet for each pair.
     */
    fun createMultiPageSheets(images: List<Bitmap>): List<Bitmap> {
        return images.chunked(2).map { pair ->
            val front = pair[0]
            val back = pair.getOrNull(1)
            createA4Sheet(front, back)
        }
    }
}
