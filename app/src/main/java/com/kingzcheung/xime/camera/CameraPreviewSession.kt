package com.kingzcheung.xime.camera

import android.content.Context
import android.util.Size
import android.view.MotionEvent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Shared by text capture and pairing. Owns only its own use cases; never unbindAll(). */
internal class CameraPreviewSession(
    context: Context,
    owner: LifecycleOwner,
    private val view: PreviewView,
    resolution: Size,
    private val onFrame: (ImageProxy) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onReady: (Boolean) -> Unit = {},
) : AutoCloseable {
    private val active = AtomicBoolean(true)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = ContextCompat.getMainExecutor(context)
    private val provider = ProcessCameraProvider.getInstance(context)
    private var camera: Camera? = null
    private val preview = Preview.Builder().setTargetRotation(view.display?.rotation ?: 0).build()
        .also { it.surfaceProvider = view.surfaceProvider }
    private val analysis = ImageAnalysis.Builder()
        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(resolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
        ).build())
        .setTargetRotation(view.display?.rotation ?: 0)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

    init {
        analysis.setAnalyzer(worker) { frame ->
            try { if (active.get()) onFrame(frame) }
            finally { frame.close() }
        }
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) { focus(event.x, event.y); view.performClick() }
            true
        }
        provider.addListener({
            if (active.get()) runCatching {
                val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis)
                view.viewPort?.let(group::setViewPort)
                camera = provider.get().bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                focus(view.width / 2f, view.height / 2f)
                onReady(camera?.cameraInfo?.hasFlashUnit() == true)
            }.onFailure { if (active.get()) onError(it) }
        }, main)
    }

    private fun focus(x: Float, y: Float) {
        if (!active.get() || view.width == 0 || view.height == 0) return
        runCatching {
            val point = view.meteringPointFactory.createPoint(x, y)
            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point)
                .setAutoCancelDuration(3, TimeUnit.SECONDS).build())
        }
    }

    fun setTorch(enabled: Boolean) { if (active.get()) camera?.cameraControl?.enableTorch(enabled) }

    override fun close() {
        if (!active.compareAndSet(true, false)) return
        analysis.clearAnalyzer()
        view.setOnTouchListener(null)
        camera?.cameraControl?.enableTorch(false)
        if (provider.isDone) runCatching { provider.get().unbind(preview, analysis) }
        worker.shutdown()
    }
}
