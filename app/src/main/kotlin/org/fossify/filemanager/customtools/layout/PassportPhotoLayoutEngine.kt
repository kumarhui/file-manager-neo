package org.fossify.filemanager.customtools.layout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

object PassportPhotoLayoutEngine {

    private const val DPI = 300f

    /*
     * Physical passport photo size.
     */
    const val PHOTO_WIDTH_MM = 30f
    const val PHOTO_HEIGHT_MM = 40f

    /*
     * Space between photos.
     */
    const val PHOTO_GAP_MM = 2f

    /*
     * Minimum white space at page edges.
     */
    const val SIDE_MARGIN_MM = 1f

    /*
     * Black passport-photo border.
     */
    const val BORDER_MM = 0.5f

    const val COPIES_PER_ROW = 6
    const val MAX_ROWS = 6

    private fun mmToPx(mm: Float): Int {
        return (mm * DPI / 25.4f).toInt()
    }

    fun createSheet(
        rows: Map<Int, Bitmap>,
        paperWidthMm: Float,
        paperHeightMm: Float
    ): Bitmap {

        val pageWidth =
            mmToPx(paperWidthMm)

        val pageHeight =
            mmToPx(paperHeightMm)

        val sheet =
            Bitmap.createBitmap(
                pageWidth,
                pageHeight,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(sheet)

        canvas.drawColor(Color.WHITE)

        val photoWidth =
            mmToPx(PHOTO_WIDTH_MM)

        val photoHeight =
            mmToPx(PHOTO_HEIGHT_MM)

        val gap =
            mmToPx(PHOTO_GAP_MM)

        val sideMargin =
            mmToPx(SIDE_MARGIN_MM)

        val borderWidth =
            mmToPx(BORDER_MM).toFloat()

        val rowHeight =
            pageHeight.toFloat() / MAX_ROWS

        val totalPhotosWidth =
            COPIES_PER_ROW * photoWidth +
                (COPIES_PER_ROW - 1) * gap

        /*
         * Center the complete six-photo strip.
         *
         * The strip is never allowed to cross
         * the minimum side-margin safe area.
         */
        val safeLeft =
            sideMargin.toFloat()

        val safeRight =
            pageWidth.toFloat() -
                sideMargin

        val safeWidth =
            safeRight - safeLeft

        val startX =
            safeLeft +
                ((safeWidth - totalPhotosWidth) / 2f)
                    .coerceAtLeast(0f)

        val borderPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    borderWidth

                color =
                    Color.BLACK
            }

        val bitmapPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
            )

        rows.forEach { (rowIndex, bitmap) ->

            if (
                rowIndex !in 0 until MAX_ROWS
            ) {
                return@forEach
            }

            val scaled =
                Bitmap.createScaledBitmap(
                    bitmap,
                    photoWidth,
                    photoHeight,
                    true
                )

            /*
             * Vertically center the exact
             * 40 mm photo inside its row.
             */
            val y =
                rowIndex * rowHeight +
                    (
                        rowHeight -
                            photoHeight
                    ) / 2f

            repeat(COPIES_PER_ROW) { index ->

                val x =
                    startX +
                        index *
                        (photoWidth + gap)

                val destination =
                    RectF(
                        x,
                        y,
                        x + photoWidth,
                        y + photoHeight
                    )

                canvas.drawBitmap(
                    scaled,
                    null,
                    destination,
                    bitmapPaint
                )

                canvas.drawRect(
                    destination,
                    borderPaint
                )
            }

            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }

        return sheet
    }

    fun createA6(
        rows: Map<Int, Bitmap>
    ): Bitmap {

        return createSheet(
            rows = rows,
            paperWidthMm = 105f,
            paperHeightMm = 148f
        )
    }

    fun createA4(
        rows: Map<Int, Bitmap>
    ): Bitmap {

        return createSheet(
            rows = rows,
            paperWidthMm = 210f,
            paperHeightMm = 297f
        )
    }
}
