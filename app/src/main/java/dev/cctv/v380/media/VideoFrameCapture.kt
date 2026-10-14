package dev.cctv.v380.media

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import dev.cctv.v380.protocol.VideoGeometry
import dev.cctv.v380.session.SessionError
import dev.cctv.v380.transport.CameraFailure
import kotlinx.coroutines.*
import kotlin.math.abs

/** Copies only the video Surface. No file, screenshot of the window, or persistent image cache. */
object VideoFrameCapture {
    suspend fun copy(view: SurfaceView, geometry: VideoGeometry, afterLayout: suspend () -> Unit = {}): Bitmap = withContext(Dispatchers.Main.immediate) {
        if (!geometry.flat) throw CameraFailure(SessionError.FeatureUnsupported)
        withTimeout(1500) {
            fun fits(w: Int, h: Int): Boolean = w > 0 && h > 0 && abs(w - h * geometry.width.toDouble() / geometry.height) <= 1.5
            while (!view.isAttachedToWindow || !view.holder.surface.isValid || !fits(view.width, view.height) ||
                !fits(view.holder.surfaceFrame.width(), view.holder.surfaceFrame.height())) delay(16)
            afterLayout()
            if (!view.holder.surface.isValid || !fits(view.width, view.height) ||
                !fits(view.holder.surfaceFrame.width(), view.holder.surfaceFrame.height())) throw CameraFailure(SessionError.FeatureUnsupported)
            val (width, height) = geometry.copySize()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            suspendCancellableCoroutine { continuation ->
                try {
                    PixelCopy.request(view, bitmap, { result ->
                        if (!continuation.isActive) bitmap.recycle()
                        else if (result == PixelCopy.SUCCESS) continuation.resume(bitmap) { _, image, _ -> image.recycle() }
                        else {
                            bitmap.recycle()
                            continuation.resumeWith(Result.failure(CameraFailure(SessionError.FeatureUnsupported)))
                        }
                    }, Handler(Looper.getMainLooper()))
                } catch (error: Exception) {
                    bitmap.recycle()
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                }
            }
        }
    }
}
