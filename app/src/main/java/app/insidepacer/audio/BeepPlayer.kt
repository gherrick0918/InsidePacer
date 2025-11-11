package app.insidepacer.audio

import android.media.AudioManager
import android.media.ToneGenerator

class BeepPlayer {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    fun playTone(toneType: Int, durationMs: Int) {
        toneGenerator.startTone(toneType, durationMs)
    }

    fun release() {
        toneGenerator.release()
    }
}
