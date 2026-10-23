package io.github.masoudd2159.deviceatlas.data.interactive

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import javax.inject.Inject

class AndroidInteractiveHaptics @Inject constructor(@ApplicationContext private val context: Context) : InteractiveHaptics {
    private var owned: Vibrator? = null
    @Suppress("DEPRECATION")
    override fun pulse(millis: Long) {
        require(millis in 1..100)
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                else context.getSystemService(Vibrator::class.java)
            if (vibrator == null || !vibrator.hasVibrator()) throw InteractiveFailure(InteractiveUnavailable.NOT_PRESENT)
            owned = vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (failure: InteractiveFailure) { throw failure
        } catch (_: RuntimeException) { cancel(); throw InteractiveFailure(InteractiveUnavailable.PLATFORM) }
    }
    override fun cancel() {
        val vibrator = owned; owned = null
        try { vibrator?.cancel() } catch (_: RuntimeException) { /* Effect is independently bounded to 100 ms. */ }
    }
}
