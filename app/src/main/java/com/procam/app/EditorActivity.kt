package com.procam.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.MediaController
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.procam.app.databinding.ActivityEditorBinding
import java.text.SimpleDateFormat
import java.util.Locale

class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private lateinit var sourceUri: Uri
    private var isVideo = false
    private var originalBitmap: Bitmap? = null
    private var selectedPreset: Preset = Preset.NATURAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        sourceUri = Uri.parse(intent.getStringExtra("uri"))
        isVideo = intent.getBooleanExtra("isVideo", false)

        if (isVideo) {
            binding.previewVideo.visibility = android.view.View.VISIBLE
            binding.editorPresetSpinner.visibility = android.view.View.GONE
            binding.brightnessSeek.visibility = android.view.View.GONE
            binding.btnSave.visibility = android.view.View.GONE
            val controller = MediaController(this)
            binding.previewVideo.setMediaController(controller)
            controller.setAnchorView(binding.previewVideo)
            binding.previewVideo.setVideoURI(sourceUri)
            binding.previewVideo.start()
        } else {
            binding.previewImage.visibility = android.view.View.VISIBLE
            contentResolver.openInputStream(sourceUri)?.use {
                originalBitmap = BitmapFactory.decodeStream(it)
            }
            binding.previewImage.setImageBitmap(originalBitmap)
            setupEditorControls()
        }
    }

    private fun setupEditorControls() {
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            Preset.values().map { it.displayName }
        )
        binding.editorPresetSpinner.adapter = adapter
        binding.editorPresetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                selectedPreset = Preset.values()[pos]
                renderPreview()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        binding.brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                renderPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.btnSave.setOnClickListener { saveEditedCopy() }
    }

    private fun renderPreview() {
        val src = originalBitmap ?: return
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val cm = ColorMatrix(selectedPreset.colorMatrix().array.copyOf())
        val brightnessOffset = (binding.brightnessSeek.progress - 100).toFloat()
        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightnessOffset,
                0f, 1f, 0f, 0f, brightnessOffset,
                0f, 0f, 1f, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(brightnessMatrix)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        binding.previewImage.setImageBitmap(result)
    }

    private fun saveEditedCopy() {
        val drawable = binding.previewImage.drawable ?: return
        val bmp = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
        val name = "ProCam_edit_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ProCam")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            Toast.makeText(this, "Сохранено как копия", Toast.LENGTH_SHORT).show()
        }
    }
}
