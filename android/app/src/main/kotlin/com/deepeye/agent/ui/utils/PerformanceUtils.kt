package com.deepeye.agent.ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

enum class DeepEyeHapticType {
    CLICK,
    LIGHT_IMPACT,
    HEAVY_IMPACT,
    SUCCESS,
    WARNING,
    ERROR
}

object PerformanceUtils {

    /**
     * Executes optimized haptic feedback using Android system Vibrator APIs when available,
     * falling back gracefully to Compose HapticFeedback.
     */
    fun triggerHaptic(context: Context, type: DeepEyeHapticType, composeHaptic: HapticFeedback? = null) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effect = when (type) {
                        DeepEyeHapticType.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        DeepEyeHapticType.LIGHT_IMPACT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        DeepEyeHapticType.HEAVY_IMPACT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        DeepEyeHapticType.SUCCESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        DeepEyeHapticType.WARNING -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                        DeepEyeHapticType.ERROR -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                    vibrator.vibrate(effect)
                    return
                }
            }
        } catch (_: Exception) {
            // Fall through to Compose fallback
        }

        composeHaptic?.let { haptic ->
            when (type) {
                DeepEyeHapticType.CLICK, DeepEyeHapticType.LIGHT_IMPACT ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                DeepEyeHapticType.HEAVY_IMPACT, DeepEyeHapticType.SUCCESS, DeepEyeHapticType.WARNING, DeepEyeHapticType.ERROR ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    /**
     * Optional spatial audio trigger hook for futuristic audio cues.
     */
    fun triggerSpatialAudioCue(context: Context, cueName: String) {
        // Safe spatial audio hook placeholder for sound pool or spatializer audio feedback
    }

    /**
     * ContentType constants for LazyColumn performance optimization.
     */
    object ContentTypes {
        const val CHAT_USER_ROW = "chat_user_row"
        const val CHAT_ASSISTANT_ROW = "chat_assistant_row"
        const val CHAT_ERROR_ROW = "chat_error_row"
        const val BENCHMARK_CARD = "benchmark_card"
        const val MODEL_CARD = "model_card"
        const val BENTO_CELL = "bento_cell"
    }
}
