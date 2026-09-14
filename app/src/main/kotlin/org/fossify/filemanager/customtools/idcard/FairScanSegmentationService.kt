package org.fossify.filemanager.customtools.idcard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ScanPoint(val x: Double, val y: Double)
data class ScanQuad(
    val topLeft: ScanPoint,
    val topRight: ScanPoint,
    val bottomRight: ScanPoint,
    val bottomLeft: ScanPoint
)

class FairScanSegmentationService(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val inferenceLock = Mutex()

    init {
        try {
            val fileDescriptor = context.assets.openFd("fairscan-segmentation-model.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply { numThreads = 2 })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun detectQuad(bitmap: Bitmap): ScanQuad = inferenceLock.withLock {
        val activeInterpreter = interpreter ?: return@withLock detectPaperQuadFallback(bitmap)

        val inputShape = activeInterpreter.getInputTensor(0).shape()
        val inH = inputShape[1]
        val inW = inputShape[2]

        val outputShape = activeInterpreter.getOutputTensor(0).shape()
        val outH = outputShape[1]
        val outW = outputShape[2]

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inW, inH, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inH * inW * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
            rewind()
        }

        val intValues = IntArray(inW * inH)
        scaledBitmap.getPixels(intValues, 0, inW, 0, 0, inW, inH)
        if (scaledBitmap != bitmap) scaledBitmap.recycle()

        for (pixel in intValues) {
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)
            inputBuffer.putFloat((r - 127.5f) / 127.5f)
            inputBuffer.putFloat((g - 127.5f) / 127.5f)
            inputBuffer.putFloat((b - 127.5f) / 127.5f)
        }
        inputBuffer.rewind()

        val outputBuffer = ByteBuffer.allocateDirect(1 * outH * outW * 4).apply {
            order(ByteOrder.nativeOrder())
            rewind()
        }

        activeInterpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        val floatBuffer = outputBuffer.asFloatBuffer()
        val probMap = FloatArray(outW * outH)
        floatBuffer.get(probMap)

        val threshold = 0.50f
        var minX = outW.toFloat(); var maxX = 0f
        var minY = outH.toFloat(); var maxY = 0f
        var count = 0

        for (y in 0 until outH) {
            val offset = y * outW
            for (x in 0 until outW) {
                if (probMap[offset + x] >= threshold) {
                    if (x < minX) minX = x.toFloat()
                    if (x > maxX) maxX = x.toFloat()
                    if (y < minY) minY = y.toFloat()
                    if (y > maxY) maxY = y.toFloat()
                    count++
                }
            }
        }

        if (count < 80 || (maxX - minX) < outW * 0.25f || (maxY - minY) < outH * 0.25f) {
            return@withLock detectPaperQuadFallback(bitmap)
        }

        val padX = (maxX - minX) * 0.015f
        val padY = (maxY - minY) * 0.015f

        ScanQuad(
            topLeft = ScanPoint(((minX - padX) / outW).toDouble().coerceIn(0.0, 1.0), ((minY - padY) / outH).toDouble().coerceIn(0.0, 1.0)),
            topRight = ScanPoint(((maxX + padX) / outW).toDouble().coerceIn(0.0, 1.0), ((minY - padY) / outH).toDouble().coerceIn(0.0, 1.0)),
            bottomRight = ScanPoint(((maxX + padX) / outW).toDouble().coerceIn(0.0, 1.0), ((maxY + padY) / outH).toDouble().coerceIn(0.0, 1.0)),
            bottomLeft = ScanPoint(((minX - padX) / outW).toDouble().coerceIn(0.0, 1.0), ((maxY + padY) / outH).toDouble().coerceIn(0.0, 1.0))
        )
    }

    private fun detectPaperQuadFallback(bitmap: Bitmap): ScanQuad {
        val originalW = bitmap.width
        val originalH = bitmap.height
        val targetScale = 500f / max(originalW, originalH).coerceAtLeast(1)
        val scaledW = (originalW * targetScale).toInt().coerceAtLeast(50)
        val scaledH = (originalH * targetScale).toInt().coerceAtLeast(50)

        val smallBmp = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        val pixels = IntArray(scaledW * scaledH)
        smallBmp.getPixels(pixels, 0, scaledW, 0, 0, scaledW, scaledH)
        if (smallBmp != bitmap) smallBmp.recycle()

        val gray = FloatArray(scaledW * scaledH)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = (0.299f * ((p shr 16) and 0xff) + 0.587f * ((p shr 8) and 0xff) + 0.114f * (p and 0xff))
        }

        val blurred = FloatArray(scaledW * scaledH)
        val kernel = floatArrayOf(
            1f, 4f, 7f, 4f, 1f,
            4f, 16f, 26f, 16f, 4f,
            7f, 26f, 41f, 26f, 7f,
            4f, 16f, 26f, 16f, 4f,
            1f, 4f, 7f, 4f, 1f
        )
        val kernelSum = 273f

        for (y in 2 until scaledH - 2) {
            for (x in 2 until scaledW - 2) {
                var sum = 0f
                var ki = 0
                for (ky in -2..2) {
                    val rowOffset = (y + ky) * scaledW
                    for (kx in -2..2) {
                        sum += gray[rowOffset + (x + kx)] * kernel[ki++]
                    }
                }
                blurred[y * scaledW + x] = sum / kernelSum
            }
        }

        val edges = FloatArray(scaledW * scaledH)
        var maxMag = 0f
        for (y in 1 until scaledH - 1) {
            val yPrev = (y - 1) * scaledW
            val yCurr = y * scaledW
            val yNext = (y + 1) * scaledW
            for (x in 1 until scaledW - 1) {
                val gx = -blurred[yPrev + x - 1] + blurred[yPrev + x + 1] -
                    2f * blurred[yCurr + x - 1] + 2f * blurred[yCurr + x + 1] -
                    blurred[yNext + x - 1] + blurred[yNext + x + 1]
                val gy = -blurred[yPrev + x - 1] - 2f * blurred[yPrev + x] - blurred[yPrev + x + 1] +
                    blurred[yNext + x - 1] + 2f * blurred[yNext + x] + blurred[yNext + x + 1]
                val mag = sqrt(gx * gx + gy * gy)
                edges[yCurr + x] = mag
                if (mag > maxMag) maxMag = mag
            }
        }

        val threshold = max(20f, maxMag * 0.22f)
        val edgePoints = ArrayList<PointF>(1000)
        for (y in 5 until scaledH - 5) {
            val rowOffset = y * scaledW
            for (x in 5 until scaledW - 5) {
                if (edges[rowOffset + x] >= threshold) {
                    edgePoints.add(PointF(x.toFloat(), y.toFloat()))
                }
            }
        }

        if (edgePoints.size < 40) {
            return ScanQuad(ScanPoint(0.05, 0.05), ScanPoint(0.95, 0.05), ScanPoint(0.95, 0.95), ScanPoint(0.05, 0.95))
        }

        var bestTL = edgePoints[0]; var minSum = bestTL.x + bestTL.y
        var bestBR = edgePoints[0]; var maxSum = bestBR.x + bestBR.y
        var bestTR = edgePoints[0]; var maxDiff = bestTR.x - bestTR.y
        var bestBL = edgePoints[0]; var minDiff = bestBL.x - bestBL.y

        for (pt in edgePoints) {
            val sum = pt.x + pt.y
            val diff = pt.x - pt.y
            if (sum < minSum) { minSum = sum; bestTL = pt }
            if (sum > maxSum) { maxSum = sum; bestBR = pt }
            if (diff > maxDiff) { maxDiff = diff; bestTR = pt }
            if (diff < minDiff) { minDiff = diff; bestBL = pt }
        }

        return ScanQuad(
            ScanPoint((bestTL.x / scaledW).toDouble().coerceIn(0.0, 1.0), (bestTL.y / scaledH).toDouble().coerceIn(0.0, 1.0)),
            ScanPoint((bestTR.x / scaledW).toDouble().coerceIn(0.0, 1.0), (bestTR.y / scaledH).toDouble().coerceIn(0.0, 1.0)),
            ScanPoint((bestBR.x / scaledW).toDouble().coerceIn(0.0, 1.0), (bestBR.y / scaledH).toDouble().coerceIn(0.0, 1.0)),
            ScanPoint((bestBL.x / scaledW).toDouble().coerceIn(0.0, 1.0), (bestBL.y / scaledH).toDouble().coerceIn(0.0, 1.0))
        )
    }

    fun perspectiveWarp(bitmap: Bitmap, quad: ScanQuad): Bitmap {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        val p0 = floatArrayOf((quad.topLeft.x * w).toFloat(), (quad.topLeft.y * h).toFloat())
        val p1 = floatArrayOf((quad.topRight.x * w).toFloat(), (quad.topRight.y * h).toFloat())
        val p2 = floatArrayOf((quad.bottomRight.x * w).toFloat(), (quad.bottomRight.y * h).toFloat())
        val p3 = floatArrayOf((quad.bottomLeft.x * w).toFloat(), (quad.bottomLeft.y * h).toFloat())

        val topW = hypot(p1[0] - p0[0], p1[1] - p0[1])
        val bottomW = hypot(p2[0] - p3[0], p2[1] - p3[1])
        val leftH = hypot(p3[0] - p0[0], p3[1] - p0[1])
        val rightH = hypot(p2[0] - p1[0], p2[1] - p1[1])

        val targetWidth = max(topW, bottomW).toInt().coerceAtLeast(100)
        val targetHeight = max(leftH, rightH).toInt().coerceAtLeast(100)

        val src = floatArrayOf(p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1])
        val dst = floatArrayOf(0f, 0f, targetWidth.toFloat(), 0f, targetWidth.toFloat(), targetHeight.toFloat(), 0f, targetHeight.toFloat())

        val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }
}
