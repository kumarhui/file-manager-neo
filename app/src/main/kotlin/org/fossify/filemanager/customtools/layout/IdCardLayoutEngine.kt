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
     * Creates an A4 sheet containing up to 2 ID card images anchored at the top.
     */
    fun createA4Sheet(
        front: Bitmap,
        back: Bitmap? = null,
        isVertical: Boolean = true
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

        val topMargin = A4Page.mmToPx(20f) // Clean top offset from edge of A4 page

        if (back == null) {
            val centerX = (pageWidth - cardWidth) / 2f
            val dest = RectF(centerX, topMargin, centerX + cardWidth, topMargin + cardHeight)
            canvas.drawBitmap(front, null, dest, paint)
            canvas.drawRect(dest, strokePaint)
        } else {
            val gapPx = A4Page.mmToPx(10f) // Tighter gap between cards

            if (isVertical) {
                // Vertical Stack: Anchored at the top
                val centerX = (pageWidth - cardWidth) / 2f

                val frontDest = RectF(centerX, topMargin, centerX + cardWidth, topMargin + cardHeight)
                canvas.drawBitmap(front, null, frontDest, paint)
                canvas.drawRect(frontDest, strokePaint)

                val backY = topMargin + cardHeight + gapPx
                val backDest = RectF(centerX, backY, centerX + cardWidth, backY + cardHeight)
                canvas.drawBitmap(back, null, backDest, paint)
                canvas.drawRect(backDest, strokePaint)
            } else {
                // Horizontal Stack: Left and Right side-by-side anchored at the top
                val totalWidth = (cardWidth * 2) + gapPx
                val startX = (pageWidth - totalWidth) / 2f

                val frontDest = RectF(startX, topMargin, startX + cardWidth, topMargin + cardHeight)
                canvas.drawBitmap(front, null, frontDest, paint)
                canvas.drawRect(frontDest, strokePaint)

                val backX = startX + cardWidth + gapPx
                val backDest = RectF(backX, topMargin, backX + cardWidth, topMargin + cardHeight)
                canvas.drawBitmap(back, null, backDest, paint)
                canvas.drawRect(backDest, strokePaint)
            }
        }

        return result
    }

    fun createMultiPageSheets(images: List<Bitmap>, isVertical: Boolean = true): List<Bitmap> {
        return images.chunked(2).map { pair ->
            val front = pair[0]
            val back = pair.getOrNull(1)
            createA4Sheet(front, back, isVertical)
        }
    }
}