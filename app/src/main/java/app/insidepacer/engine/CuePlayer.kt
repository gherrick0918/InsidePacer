// app/src/main/java/app/insidepacer/engine/CuePlayer.kt
package app.insidepacer.engine

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class CuePlayer(ctx: Context) {
    private val tts = TextToSpeech(ctx) { }
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, /*vol%*/ 80)

    // waiters let us suspend until a given utterance finishes speaking
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    init {
        tts.language = Locale.getDefault()
        // slightly slower = clearer “3, 2, 1”
        tts.setSpeechRate(0.95f)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Beep *right* as each number starts
                if (utteranceId?.startsWith("count_") == true) {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, /*ms*/ 140)
                }
            }
            override fun onDone(id: String?) {
                id?.let { waiters.remove(it)?.complete(Unit) }
            }
            override fun onError(id: String?) {
                id?.let { waiters.remove(it)?.complete(Unit) }
            }
        })
    }

    private suspend fun speakAwait(id: String, text: String) {
        val def = CompletableDeferred<Unit>().also { waiters[id] = it }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        // Safety timeout in case some engines never call onDone
        withTimeout(3_000L) { def.await() }
    }

    suspend fun countdown321Aligned() {
        speakAwait("count_3", "3")
        speakAwait("count_2", "2")
        speakAwait("count_1", "1")
        speakAwait("count_go", "Go")
    }

    fun preChangeTo(next: Double?) {
        if (next != null) tts.speak("Speed change to ${"%.1f".format(next)} in ten seconds",
            TextToSpeech.QUEUE_ADD, null, "pre_${System.nanoTime()}")
    }

    fun changeNowTo(next: Double?) {
        val id = "chg_${System.nanoTime()}"
        if (next != null) tts.speak("Change speed now to ${"%.1f".format(next)}", TextToSpeech.QUEUE_ADD, null, id)
        else tts.speak("Session complete", TextToSpeech.QUEUE_ADD, null, id)
    }

    fun beep(ms: Int = 140) { tone.startTone(ToneGenerator.TONE_PROP_BEEP, ms) }

    fun release() {
        try { tts.shutdown() } catch (_: Throwable) {}
        try { tone.release() } catch (_: Throwable) {}
    }
}
