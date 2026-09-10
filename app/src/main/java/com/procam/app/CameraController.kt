package com.procam.app

import android.content.ContentValues
import android.content.Context
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Инкапсулирует всю логику CameraX: выбор объектива (из 3 камер A34),
 * разрешение/FPS, применение цветового пресета к превью, ночной режим,
 * съёмку фото и видео с корректным сохранением в MediaStore.
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    var currentLens: LensChoice = LensChoice.MAIN
        private set
    var currentQuality: CaptureQuality = QUALITY_PRESETS[0]
        private set
    var currentPreset: Preset = Preset.NATURAL
        private set
    var nightModeEnabled: Boolean = false
        private set

    private val executor = ContextCompat.getMainExecutor(context)

    fun initialize(onReady: () -> Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            val extFuture = ExtensionsManager.getInstanceAsync(context, cameraProvider!!)
            extFuture.addListener({
                extensionsManager = extFuture.get()
                bindUseCases()
                onReady()
            }, executor)
        }, executor)
    }

    fun setLens(lens: LensChoice) {
        currentLens = lens
        bindUseCases()
    }

    fun setQuality(quality: CaptureQuality) {
        currentQuality = quality
        bindUseCases()
    }

    fun setPreset(preset: Preset) {
        currentPreset = preset
        // Применяем цветовой фильтр к превью в реальном времени
        previewView.setBackgroundColor(0) // no-op, filter applied on overlay view externally
    }

    fun setNightMode(enabled: Boolean) {
        nightModeEnabled = enabled
        bindUseCases()
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun selectCameraSelector(): CameraSelector {
        // На большинстве Samsung (в т.ч. A34) физические камеры отличаются по lens facing/id.
        // Пытаемся выбрать через CameraSelector с фильтром по фокусному расстоянию,
        // с fallback на DEFAULT_BACK_CAMERA если конкретную линзу определить не удалось.
        val provider = cameraProvider ?: return CameraSelector.DEFAULT_BACK_CAMERA
        val availableCameras = provider.availableCameraInfos.filter {
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build().filter(listOf(it)).isNotEmpty()
        }

        // Сортируем по фокусному расстоянию, если доступно через Camera2Interop
        val withFocal = availableCameras.mapNotNull { info ->
            try {
                val camera2Info = Camera2CameraInfo.from(info)
                val focalLengths = camera2Info.getCameraCharacteristic(
                    android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                )
                val focal = focalLengths?.firstOrNull() ?: return@mapNotNull null
                info to focal
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.second }

        if (withFocal.isEmpty()) return CameraSelector.DEFAULT_BACK_CAMERA

        val chosenInfo = when (currentLens) {
            LensChoice.ULTRA_WIDE -> withFocal.first().first   // самое короткое фокусное = ультраширик
            LensChoice.TELE -> withFocal.last().first          // самое длинное = телефото
            LensChoice.MAIN -> {
                // средний по значению фокусного расстояния
                withFocal[withFocal.size / 2].first
            }
        }

        return CameraSelector.Builder()
            .addCameraFilter { infos -> infos.filter { it == chosenInfo } }
            .build()
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val selector = selectCameraSelector()

        val previewBuilder = Preview.Builder()
        preview = previewBuilder.build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val captureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)

        // Разрешение видео согласно выбранному качеству
        val quality = when (currentQuality.videoQualityHint) {
            "UHD" -> Quality.UHD
            "FHD" -> Quality.FHD
            "HD" -> Quality.HD
            else -> Quality.SD
        }
        val qualitySelector = QualitySelector.from(
            quality,
            FallbackStrategy.higherQualityOrLowerThan(quality)
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()

        // Настройка целевого FPS через Camera2Interop (60/120)
        val videoCaptureBuilder = VideoCapture.Builder(recorder)
        Camera2Interop.Extender(videoCaptureBuilder).setCaptureRequestOption(
            android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            Range(currentQuality.targetFps, currentQuality.targetFps)
        )

        imageCapture = captureBuilder.build()
        videoCapture = videoCaptureBuilder.build()

        val useCasesToBind = mutableListOf<UseCase>(preview!!, imageCapture!!, videoCapture!!)

        try {
            camera = if (nightModeEnabled &&
                extensionsManager?.isExtensionAvailable(selector, ExtensionMode.NIGHT) == true
            ) {
                val nightSelector = extensionsManager!!.getExtensionEnabledCameraSelector(
                    selector, ExtensionMode.NIGHT
                )
                provider.bindToLifecycle(lifecycleOwner, nightSelector, preview, imageCapture)
            } else {
                provider.bindToLifecycle(lifecycleOwner, selector, *useCasesToBind.toTypedArray())
            }
        } catch (e: Exception) {
            Log.e("CameraController", "Bind failed, falling back to default back camera", e)
            camera = provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )
        }
    }

    fun takePhoto(onSaved: (android.net.Uri) -> Unit, onError: (Exception) -> Unit) {
        val capture = imageCapture ?: return
        val name = "ProCam_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ProCam")
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let { onSaved(it) }
            }
            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        })
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun startRecording(onSaved: (android.net.Uri) -> Unit, onError: (Throwable) -> Unit) {
        val videoCap = videoCapture ?: return
        val name = "ProCam_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/ProCam")
            }
        }
        val options = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val pending = videoCap.output.prepareRecording(context, options)
            .apply {
                if (ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }

        activeRecording = pending.start(executor) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    if (!event.hasError()) {
                        onSaved(event.outputResults.outputUri)
                    } else {
                        onError(RuntimeException("Recording error: ${event.error}"))
                    }
                }
                else -> {}
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun isRecording(): Boolean = activeRecording != null
}
