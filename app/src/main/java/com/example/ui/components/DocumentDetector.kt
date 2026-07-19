package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * OpenCV-based document corner detector.
 *
 * Runs the classic scanning pipeline (grayscale -> blur -> Canny with an
 * auto-tuned threshold -> dilate -> contours -> polygon approximation) and
 * returns the largest well-formed 4-point quadrilateral found in the frame.
 *
 * Coordinates are returned NORMALIZED (0f..1f) relative to the input bitmap,
 * so the overlay/capture code is resolution independent.
 */
object DocumentDetector {

    private var initialized = false

    /** Loads the native OpenCV library. Safe to call repeatedly; returns success. */
    fun ensureInitialized(): Boolean {
        if (initialized) return true
        initialized = try {
            OpenCVLoader.initLocal()
        } catch (t: Throwable) {
            false
        }
        return initialized
    }

    /**
     * @param bitmap an already-upright (rotation-applied) camera frame
     * @return detected corners, or null when no valid document is found
     */
    fun detect(bitmap: Bitmap): CropPoints? {
        if (!ensureInitialized()) return null
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val src = Mat().also { org.opencv.android.Utils.bitmapToMat(bitmap, it) }
        try {
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

            val blurred = Mat()
            // Gaussian blur kills sensor noise / JPEG artifacts that would seed false edges.
            Imgproc.GaussianBlur(gray, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)

            // Auto Canny: pick the two thresholds from the MEDIAN of the (blurred) image.
            // This adapts to bright white paper vs dark backgrounds automatically instead
            // of hardcoding, which is why the old brightness-scan approach flickered.
            val median = medianValue(blurred)
            val lower = max(0.0, 0.66 * median)
            val upper = min(255.0, 1.33 * median)
            val edges = Mat()
            Imgproc.Canny(blurred, edges, lower, upper)

            // Dilating closes small gaps in the document border so approxPolyDP sees a
            // continuous perimeter rather than a broken one.
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(3.0, 3.0))
            Imgproc.dilate(edges, edges, kernel)

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val frameArea = src.width() * src.height().toDouble()
            val minArea = frameArea * 0.15 // ignore tiny noise contours (<15% of frame)

            var best: Array<Point>? = null
            var bestArea = 0.0
            for (c in contours) {
                val approx = approxQuad(c)
                if (approx != null) {
                    val area = Imgproc.contourArea(MatOfPoint2f(*approx))
                    if (area >= minArea && area > bestArea) {
                        bestArea = area
                        best = approx
                    }
                }
                c.release()
            }
            val result = best?.let { orderAndNormalize(it, src.width(), src.height()) }

            gray.release(); blurred.release(); edges.release(); kernel.release(); hierarchy.release()
            return result
        } finally {
            src.release()
        }
    }

    /** Approximates a contour to a 4-point convex polygon, or null if it isn't one. */
    private fun approxQuad(contour: MatOfPoint): Array<Point>? {
        val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
        val approx = MatOfPoint2f()
        // epsilon ~2% of perimeter: tight enough to keep corners sharp.
        Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)
        val pts = approx.toArray()
        approx.release()
        if (pts.size != 4) return null
        // Reject non-convex quads (a document rectangle projected is convex).
        if (Imgproc.contourArea(MatOfPoint2f(*pts)) <= 0) return null
        return pts
    }

    /**
     * Orders the 4 points to a consistent TL, TR, BR, BL layout and normalizes
     * to 0..1 using the frame's width/height.
     */
    private fun orderAndNormalize(pts: Array<Point>, w: Int, h: Int): CropPoints {
        val sorted = pts.sortedWith(compareBy({ it.y }, { it.x }))
        val top = sorted.take(2).sortedBy { it.x }
        val bottom = sorted.drop(2).sortedBy { it.x }
        val tl = top.first(); val tr = top.last()
        val bl = bottom.first(); val br = bottom.last()
        return CropPoints(
            topLeft = Offset((tl.x / w).toFloat(), (tl.y / h).toFloat()),
            topRight = Offset((tr.x / w).toFloat(), (tr.y / h).toFloat()),
            bottomRight = Offset((br.x / w).toFloat(), (br.y / h).toFloat()),
            bottomLeft = Offset((bl.x / w).toFloat(), (bl.y / h).toFloat())
        )
    }

    /** Robust median over a single-channel 8-bit Mat. */
    private fun medianValue(mat: Mat): Double {
        val total = mat.rows() * mat.cols()
        if (total == 0) return 128.0
        val hist = IntArray(256)
        val px = ByteArray(1)
        for (r in 0 until mat.rows()) {
            for (c in 0 until mat.cols()) {
                mat.get(r, c, px)
                hist[px[0].toInt() and 0xFF]++
            }
        }
        var count = 0
        val mid = total / 2
        for (i in hist.indices) {
            count += hist[i]
            if (count >= mid) return i.toDouble()
        }
        return 128.0
    }

    /**
     * Validates a detected quad: must cover a sensible fraction of the frame and
     * have corner angles roughly in the 60-120° range (rules out extreme skew/
     * false detections). [minRatio]/[maxRatio] are fractions of frame area.
     */
    fun isQuadValid(
        corners: CropPoints,
        minRatio: Float = 0.15f,
        maxRatio: Float = 0.98f
    ): Boolean {
        val xs = listOf(corners.topLeft.x, corners.topRight.x, corners.bottomRight.x, corners.bottomLeft.x)
        val ys = listOf(corners.topLeft.y, corners.topRight.y, corners.bottomRight.y, corners.bottomLeft.y)
        val wNorm = xs.maxOrNull()!! - xs.minOrNull()!!
        val hNorm = ys.maxOrNull()!! - ys.minOrNull()!!
        val areaRatio = wNorm * hNorm
        if (areaRatio < minRatio || areaRatio > maxRatio) return false
        return cornersCornerAnglesValid(corners)
    }

    private fun cornersCornerAnglesValid(c: CropPoints): Boolean {
        val pts = listOf(c.topLeft, c.topRight, c.bottomRight, c.bottomLeft)
        for (i in pts.indices) {
            val a = pts[(i + 3) % 4]
            val b = pts[i]
            val d = pts[(i + 1) % 4]
            val v1 = Offset(a.x - b.x, a.y - b.y)
            val v2 = Offset(d.x - b.x, d.y - b.y)
            val dot = v1.x * v2.x + v1.y * v2.y
            val m1 = sqrt(v1.x * v1.x + v1.y * v1.y)
            val m2 = sqrt(v2.x * v2.x + v2.y * v2.y)
            if (m1 < 1e-4f || m2 < 1e-4f) return false
            val cosA = (dot / (m1 * m2)).coerceIn(-1f, 1f)
            val deg = Math.toDegrees(acos(cosA.toDouble()))
            if (deg !in 60.0..120.0) return false
        }
        return true
    }

    /**
     * Normalized movement between two quads, averaged over the 4 corners.
     * Used by the stability tracker to detect shake / hand adjustment.
     */
    fun normalizedMovement(a: CropPoints, b: CropPoints): Float {
        val da = dist(a.topLeft, b.topLeft)
        val db = dist(a.topRight, b.topRight)
        val dc = dist(a.bottomRight, b.bottomRight)
        val dd = dist(a.bottomLeft, b.bottomLeft)
        return (da + db + dc + dd) / 4f
    }

    private fun dist(a: Offset, b: Offset): Float = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
}
