package app.insidepacer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CueDuckingManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mutex = Mutex()

    suspend fun playBeep(play: suspend () -> Unit) {
        withFocus(BEEP_ATTRIBUTES, play)
    }

    suspend fun speakTts(speak: suspend () -> Unit) {
        withFocus(TTS_ATTRIBUTES, speak)
    }

    private suspend fun <T> withFocus(attributes: AudioAttributes, block: suspend () -> T): T {
        return mutex.withLock {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager.requestAudioFocus(request)
            return@withLock try {
                block()
            } finally {
                audioManager.abandonAudioFocusRequest(request)
            }
        }
    }

    private companion object {
        val BEEP_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val TTS_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
