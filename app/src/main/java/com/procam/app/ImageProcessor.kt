package com.procam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import java.io.OutputStream

object ImageProcessor {

    fun applyPresetToUri(context: Context, uri: Uri, preset: Preset) {
        if (preset == Preset.NATURAL) return

        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val processedBitmap = applyPresetToBitmap(bitmap, preset)

            val outputStream: OutputStream = contentResolver.openOutputStream(uri, "wt") ?: return
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyPresetToBitmap(source: Bitmap, preset: Preset): Bitmap {
        val width = source.width
        val height = source.height
        val outputBitmap = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)

        val canvas = Canvas(outputBitmap)
        val paint = Paint()

        val colorMatrix = ColorMatrix()

        when (preset) {
            Preset.NATURAL -> {
                // Без изменений
            }
            Preset.WARM_AMBER -> {
                // Увеличиваем красный и зеленый (теплый оттенок)
                colorMatrix.set(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, 20f,
                        0f, 1.1f, 0f, 0f, 10f,
                        0f, 0f, 0.9f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            Preset.TEAL_NIGHT -> {
                // Холодный сине-зеленый оттенок
                colorMatrix.set(
                    floatArrayOf(
                        0.9f, 0f, 0f, 0f, -10f,
                        0f, 1.0f, 0f, 0f, 0f,
                        0f, 0f, 1.3f, 0f, 30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            Preset.NEON_GREEN -> {
                // Акцент на зеленый и контраст
                colorMatrix.set(
                    floatArrayOf(
                        1.0f, 0f, 0f, 0f, -10f,
                        0f, 1.3f, 0f, 0f, 20f,
                        0f, 0f, 0.9f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            Preset.MOODY_STREET -> {
                // Темноватый кинематографичный стиль
                colorMatrix.set(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, -20f,
                        0f, 1.1f, 0f, 0f, -20f,
                        0f, 0f, 1.2f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return outputBitmap
    }
}

