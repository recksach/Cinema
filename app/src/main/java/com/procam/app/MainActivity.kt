package com.procam.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.procam.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController
    private var isRecording = false // Отслеживание состояния записи для обычной кнопки

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            Toast.makeText(this, "Нужен доступ к камере для работы приложения", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupControls()

        if (hasAllPermissions()) {
            startCamera()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        cameraController = CameraController(this, this, binding.previewView)
        cameraController.initialize {
            runOnUiThread { Toast.makeText(this, "Камера готова", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun setupSpinners() {
        val presetAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            Preset.values().map { it.displayName }
        )
        binding.presetSpinner.adapter = presetAdapter
        binding.presetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                val preset = Preset.values()[pos]
                cameraController.setPreset(preset)
                applyPreviewFilter(preset)
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        val qualityAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            QUALITY_PRESETS.map { it.label }
        )
        binding.qualitySpinner.adapter = qualityAdapter
        binding.qualitySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                cameraController.setQuality(QUALITY_PRESETS[pos])
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    private fun applyPreviewFilter(preset: Preset) {
        val overlayColor = when (preset) {
            Preset.NATURAL -> 0x00000000
            Preset.WARM_AMBER -> 0x22FF8A00
            Preset.TEAL_NIGHT -> 0x2200708A
            Preset.NEON_GREEN -> 0x2200FF66
            Preset.MOODY_STREET -> 0x22101018
        }
        binding.presetOverlay.setBackgroundColor(overlayColor.toInt())
        binding.presetOverlay.alpha = if (preset == Preset.NATURAL) 0f else 1f
    }

    private fun setupControls() {
        binding.toggleNight.setOnCheckedChangeListener { _, checked ->
            if (::cameraController.isInitialized) cameraController.setNightMode(checked)
        }

        binding.btnLensMain.setOnClickListener {
            if (::cameraController.isInitialized) cameraController.setLens(LensChoice.MAIN)
        }
        binding.btnLensUltraWide.setOnClickListener {
            if (::cameraController.isInitialized) cameraController.setLens(LensChoice.ULTRA_WIDE)
        }
        binding.btnLensTele.setOnClickListener {
            if (::cameraController.isInitialized) cameraController.setLens(LensChoice.TELE)
        }

        binding.btnPhoto.setOnClickListener {
            if (!::cameraController.isInitialized) return@setOnClickListener
            cameraController.takePhoto(
                onSaved = { uri ->
                    runOnUiThread { Toast.makeText(this, "Фото сохранено", Toast.LENGTH_SHORT).show() }
                    applyPresetToSavedPhoto(uri)
                },
                onError = { e ->
                    runOnUiThread { Toast.makeText(this, "Ошибка съёмки: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            )
        }

        // Исправлено: заменено на setOnClickListener для обычной кнопки, с переключением состояния записи
        binding.btnVideo.setOnClickListener {
            if (!::cameraController.isInitialized) return@setOnClickListener
            
            if (!isRecording) {
                cameraController.startRecording(
                    onSaved = { uri ->
                        runOnUiThread { Toast.makeText(this, "Видео сохранено", Toast.LENGTH_SHORT).show() }
                    },
                    onError = { e ->
                        runOnUiThread { Toast.makeText(this, "Ошибка записи: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                )
                isRecording = true
                binding.btnVideo.text = "Остановить"
            } else {
                cameraController.stopRecording()
                isRecording = false
                binding.btnVideo.text = "Видео"
            }
        }

        binding.btnGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
    }

    private fun applyPresetToSavedPhoto(uri: Uri) {
        val preset = Preset.values()[binding.presetSpinner.selectedItemPosition]
        if (preset == Preset.NATURAL) return
        ImageProcessor.applyPresetToUri(this, uri, preset)
    }
}
