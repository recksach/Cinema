package com.procam.app

import android.graphics.ColorMatrix

/**
 * Цветовые пресеты, подобранные под референсы:
 * - WARM_AMBER: тёплый оранжевый/закатный (очки, шарф, парк)
 * - TEAL_NIGHT: холодный тёмно-бирюзовый ночной (кухня, гараж)
 * - NEON_GREEN: неоновый зелёный/киберпанк (метро)
 * - MOODY_STREET: контрастный уличный ночной (7-Eleven, сигарета)
 * - NATURAL: без изменений, только лёгкая коррекция контраста
 */
enum class Preset(val displayName: String) {
    NATURAL("Натурал"),
    WARM_AMBER("Тёплый янтарь"),
    TEAL_NIGHT("Ночная бирюза"),
    NEON_GREEN("Неон"),
    MOODY_STREET("Улица");

    /** Матрица для live-preview / фото пост-обработки (порядок R,G,B,A) */
    fun colorMatrix(): ColorMatrix {
        val cm = ColorMatrix()
        when (this) {
            NATURAL -> {
                // лёгкий +контраст
                applyContrast(cm, 1.08f)
            }
            WARM_AMBER -> {
                applyContrast(cm, 1.12f)
                applyColorScale(cm, rMul = 1.18f, gMul = 1.02f, bMul = 0.82f)
                applySaturation(cm, 1.15f)
            }
            TEAL_NIGHT -> {
                applyContrast(cm, 1.18f)
                applyColorScale(cm, rMul = 0.85f, gMul = 1.02f, bMul = 1.22f)
                applySaturation(cm, 0.9f)
                applyBrightness(cm, -10f)
            }
            NEON_GREEN -> {
                applyContrast(cm, 1.2f)
                applyColorScale(cm, rMul = 0.8f, gMul = 1.35f, bMul = 1.05f)
                applySaturation(cm, 1.3f)
            }
            MOODY_STREET -> {
                applyContrast(cm, 1.25f)
                applyColorScale(cm, rMul = 1.05f, gMul = 0.98f, bMul = 0.95f)
                applySaturation(cm, 0.85f)
                applyBrightness(cm, -8f)
            }
        }
        return cm
    }

    private fun applyContrast(cm: ColorMatrix, contrast: Float) {
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val m = floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(m))
    }

    private fun applySaturation(cm: ColorMatrix, sat: Float) {
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(sat)
        cm.postConcat(satMatrix)
    }

    private fun applyBrightness(cm: ColorMatrix, offset: Float) {
        val m = floatArrayOf(
            1f, 0f, 0f, 0f, offset,
            0f, 1f, 0f, 0f, offset,
            0f, 0f, 1f, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(m))
    }

    private fun applyColorScale(cm: ColorMatrix, rMul: Float, gMul: Float, bMul: Float) {
        val m = floatArrayOf(
            rMul, 0f, 0f, 0f, 0f,
            0f, gMul, 0f, 0f, 0f,
            0f, 0f, bMul, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(m))
    }
}

/** Настройки качества съёмки, выбираемые пользователем */
data class CaptureQuality(
    val label: String,
    val videoQualityHint: String, // "SD","HD","FHD","UHD"
    val targetFps: Int            // 30, 60, 120
)

val QUALITY_PRESETS = listOf(
    CaptureQuality("FHD 60fps", "FHD", 60),
    CaptureQuality("FHD 120fps (slow-mo)", "FHD", 120),
    CaptureQuality("4K 30fps", "UHD", 30),
    CaptureQuality("4K 60fps (если поддерживается)", "UHD", 60)
)

enum class LensChoice(val displayName: String) {
    MAIN("Основная"),
    ULTRA_WIDE("Сверхширокая"),
    TELE("Телефото")
}
