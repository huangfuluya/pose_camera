package com.example.posecamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import kotlin.math.sqrt

/**
 * Utility functions for basic image style matching.
 *
 * The algorithm works by computing per-channel statistics (mean and standard deviation)
 * of both the reference image and the captured photo, and then adjusting the captured
 * photo so that its statistics match those of the reference.  This is the
 * "histogram matching" / "colour transfer" approach described by Reinhard et al.
 */
object ImageStyleUtils {

    /**
     * Applies a colour-statistics transfer from [reference] to [source].
     *
     * @param source    The photo taken by the user.
     * @param reference The reference image chosen by the user.
     * @param strength  A value in [0,1] that blends between the original and the
     *                  fully-transferred result (1 = full transfer).
     * @return          A new [Bitmap] with the style applied.
     */
    fun applyStyleTransfer(source: Bitmap, reference: Bitmap, strength: Float = 1f): Bitmap {
        val srcStats = computeChannelStats(source)
        val refStats = computeChannelStats(reference)

        // Build a ColorMatrix that maps src statistics → ref statistics for each channel.
        // For channel c:  out_c = (in_c - src_mean_c) * (ref_std_c / src_std_c) + ref_mean_c
        // This can be expressed as a scale + translate per channel.
        val colorMatrix = buildTransferMatrix(srcStats, refStats)

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        return if (strength >= 1f) {
            result
        } else {
            blend(source, result, strength)
        }
    }

    // ---- Private helpers ----

    /** Per-channel (R, G, B) mean and standard deviation, normalised to [0, 1]. */
    data class ChannelStats(
        val rMean: Float, val rStd: Float,
        val gMean: Float, val gStd: Float,
        val bMean: Float, val bStd: Float
    )

    private fun computeChannelStats(bitmap: Bitmap): ChannelStats {
        val scaled = scaleBitmap(bitmap, 200)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

        var rSum = 0.0; var gSum = 0.0; var bSum = 0.0
        for (p in pixels) {
            rSum += p.red / 255.0
            gSum += p.green / 255.0
            bSum += p.blue / 255.0
        }
        val n = pixels.size.toDouble()
        val rMean = (rSum / n).toFloat()
        val gMean = (gSum / n).toFloat()
        val bMean = (bSum / n).toFloat()

        var rVar = 0.0; var gVar = 0.0; var bVar = 0.0
        for (p in pixels) {
            val rd = p.red / 255.0 - rMean
            val gd = p.green / 255.0 - gMean
            val bd = p.blue / 255.0 - bMean
            rVar += rd * rd
            gVar += gd * gd
            bVar += bd * bd
        }
        val rStd = sqrt(rVar / n).toFloat().coerceAtLeast(0.001f)
        val gStd = sqrt(gVar / n).toFloat().coerceAtLeast(0.001f)
        val bStd = sqrt(bVar / n).toFloat().coerceAtLeast(0.001f)

        return ChannelStats(rMean, rStd, gMean, gStd, bMean, bStd)
    }

    /**
     * Builds a [ColorMatrix] that maps source statistics to reference statistics.
     *
     * Android's ColorMatrix layout (row-major, 4×5):
     * [ a, 0, 0, 0, t ]  – red channel
     * [ 0, a, 0, 0, t ]  – green channel
     * [ 0, 0, a, 0, t ]  – blue channel
     * [ 0, 0, 0, 1, 0 ]  – alpha passthrough
     *
     * where  a = refStd / srcStd
     *        t = refMean - a * srcMean        (values in [0,255] range)
     */
    private fun buildTransferMatrix(src: ChannelStats, ref: ChannelStats): ColorMatrix {
        val rScale = ref.rStd / src.rStd
        val gScale = ref.gStd / src.gStd
        val bScale = ref.bStd / src.bStd

        val rTranslate = (ref.rMean - rScale * src.rMean) * 255f
        val gTranslate = (ref.gMean - gScale * src.gMean) * 255f
        val bTranslate = (ref.bMean - bScale * src.bMean) * 255f

        // clamp scales to avoid extreme distortions
        val rs = rScale.coerceIn(0.2f, 5f)
        val gs = gScale.coerceIn(0.2f, 5f)
        val bs = bScale.coerceIn(0.2f, 5f)

        val matrix = floatArrayOf(
            rs,   0f,   0f,  0f, rTranslate,
            0f,   gs,   0f,  0f, gTranslate,
            0f,   0f,   bs,  0f, bTranslate,
            0f,   0f,   0f,  1f, 0f
        )
        return ColorMatrix(matrix)
    }

    /** Pixel-level alpha blend: out = original*(1-t) + styled*t */
    private fun blend(original: Bitmap, styled: Bitmap, t: Float): Bitmap {
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(original, 0f, 0f, null)
        val paint = Paint().apply { alpha = (t * 255).toInt().coerceIn(0, 255) }
        canvas.drawBitmap(styled, 0f, 0f, paint)
        return result
    }

    /** Scale bitmap so that the longer edge is at most [maxDim] pixels. */
    private fun scaleBitmap(bmp: Bitmap, maxDim: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= maxDim && h <= maxDim) return bmp
        return if (w > h) {
            Bitmap.createScaledBitmap(bmp, maxDim, (h * maxDim.toFloat() / w).toInt(), true)
        } else {
            Bitmap.createScaledBitmap(bmp, (w * maxDim.toFloat() / h).toInt(), maxDim, true)
        }
    }
}
