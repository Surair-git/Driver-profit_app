package com.surair.driverprofit.tracking

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.surair.driverprofit.core.DidiParser

/**
 * Lee el recuadro "ganancias del día" de DiDi: espeja la pantalla en una superficie chica
 * y, cuando se le pide, recorta la franja de arriba y le reconoce el texto.
 * Entre pedidos no procesa nada: los cuadros se descartan apenas llegan.
 */
class ScreenReader(
    private val context: Context,
    private val onReading: (amount: Double, at: Long) -> Unit,
) {
    private val thread = HandlerThread("screen-reader").apply { start() }
    private val handler = Handler(thread.looper)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    @Volatile private var captureRequested = false
    @Volatile private var processing = false
    private var width = 0
    private var height = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            release()
        }
    }

    val isRunning: Boolean get() = display != null

    fun start(resultCode: Int, data: Intent): Boolean {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = try {
            manager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener la proyección", e)
            null
        } ?: return false
        projection = p
        p.registerCallback(projectionCallback, handler)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (screenW, screenH, dpi) = screenSize(wm)
        width = ((screenW * SCALE).toInt() / 2) * 2
        height = ((screenH * SCALE).toInt() / 2) * 2
        val r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ ir ->
            val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (captureRequested && !processing) {
                captureRequested = false
                processing = true
                process(img)
            } else {
                img.close()
            }
        }, handler)
        reader = r
        display = try {
            p.createVirtualDisplay(
                "driverprofit-didi", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler,
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo crear la pantalla virtual", e)
            release()
            return false
        }
        return true
    }

    private fun screenSize(wm: WindowManager): Triple<Int, Int, Int> {
        val dpi = context.resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            Triple(b.width(), b.height(), dpi)
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            Triple(m.widthPixels, m.heightPixels, m.densityDpi)
        }
    }

    /** La próxima imagen que llegue se procesa. Si la pantalla no cambia, no llega ninguna, y no pasa nada. */
    fun requestCapture() {
        captureRequested = true
    }

    private fun process(img: Image) {
        val bitmap = try {
            toBitmap(img)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo convertir la imagen", e)
            processing = false
            return
        } finally {
            img.close()
        }
        val cropH = (bitmap.height * TOP_FRACTION).toInt().coerceAtLeast(16)
        val crop = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, cropH)
        val fullHeight = bitmap.height
        Ocr.recognize(
            crop,
            onResult = { lines ->
                processing = false
                val v = DidiParser.dailyAmount(lines, crop.width, fullHeight)
                if (v != null) onReading(v, System.currentTimeMillis())
            },
            onError = {
                processing = false
                Log.w(TAG, "Fallo el reconocimiento de texto", it)
            },
        )
    }

    private fun toBitmap(img: Image): Bitmap {
        val plane = img.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val padding = (rowStride - pixelStride * img.width) / pixelStride
        val bmp = Bitmap.createBitmap(img.width + padding, img.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(plane.buffer)
        return if (padding > 0) Bitmap.createBitmap(bmp, 0, 0, img.width, img.height) else bmp
    }

    fun release() {
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection?.let {
            runCatching { it.unregisterCallback(projectionCallback) }
            runCatching { it.stop() }
        }
        projection = null
    }

    fun destroy() {
        release()
        thread.quitSafely()
    }

    companion object {
        private const val TAG = "ScreenReader"
        /** La pantalla se espeja a este tamaño: suficiente para leer el recuadro, barato para el GPU. */
        private const val SCALE = 0.6f
        /** Solo se reconoce texto en esta franja superior. */
        private const val TOP_FRACTION = 0.25f
    }
}
