package app.insidepacer.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CueDuckingManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mutex = Mutex()

    suspend fun <T> withFocus(attributes: AudioAttributes, block: suspend () -> T): T {
        return mutex.withLock {
            val focusRequest = AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()

            val usedFallbackDuck = ensureFocus(focusRequest)
            try {
                block()
            } finally {
                audioManager.abandonAudioFocusRequest(focusRequest)
                if (usedFallbackDuck) {
                    restoreSoftDuck()
                }
            }
        }
    }

    private suspend fun ensureFocus(request: AudioFocusRequest): Boolean {
        for (i in 1..MAX_FOCUS_ATTEMPTS) {
            when (audioManager.requestAudioFocus(request)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> return false
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    delay(FOCUS_RETRY_DELAY_MS)
                }
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    break
                }
            }
            delay(FOCUS_RETRY_DELAY_MS)
        }
        applySoftDuck()
        return true
    }

    private fun applySoftDuck() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            0
        )
    }

    private fun restoreSoftDuck() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            0
        )
    }

    companion object {
        private const val MAX_FOCUS_ATTEMPTS = 3
        private const val FOCUS_RETRY_DELAY_MS = 75L
    }
}
