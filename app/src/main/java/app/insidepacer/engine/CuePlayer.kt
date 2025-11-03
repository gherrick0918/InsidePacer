package app.insidepacer.engine

import android.content.Context
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.insidepacer.data.Units
import kotlinx.coroutines.CompletableDeferred
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class CuePlayer(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts: TextToSpeech
    private val beeper = ToneGenerator(5, 100)
    @Volatile
    private var voiceOn = true

    private val ttsInitialized = CompletableDeferred<Unit>()
    private val utteranceCompletions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    init {
        tts = TextToSpeech(ctx, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) {
                    utteranceCompletions.remove(utteranceId)?.complete(Unit)
                }

                override fun onError(utteranceId: String) {
                    utteranceCompletions.remove(utteranceId)?.complete(Unit)
                }
            })
            ttsInitialized.complete(Unit)
        } else {
            ttsInitialized.completeExceptionally(RuntimeException("TTS initialization failed"))
        }
    }

    fun setVoiceEnabled(on: Boolean) {
        voiceOn = on
    }

    private suspend fun say(text: String, flush: Boolean) {
        ttsInitialized.await()
        if (!voiceOn) return

        val utteranceId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Unit>()
        utteranceCompletions[utteranceId] = deferred

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(text, queueMode, null, utteranceId)

        try {
            deferred.await()
        } catch (e: Exception) {
            utteranceCompletions.remove(utteranceId)
        }
    }

    private fun sayAsync(text: String, flush: Boolean = false) {
        if (!voiceOn || !ttsInitialized.isCompleted) return
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(text, queueMode, null, null)
    }

    fun beep() {
        beeper.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
    }

    suspend fun countdown321(delayMs: Long = 1000) {
        ttsInitialized.await()
        beep(); sayAsync("3"); kotlinx.coroutines.delay(delayMs)
        beep(); sayAsync("2"); kotlinx.coroutines.delay(delayMs)
        beep(); sayAsync("1"); kotlinx.coroutines.delay(delayMs)
        sayAsync("Go")
    }

    suspend fun announceStartingSpeed(speed: Double, units: Units) {
        say("First speed is $speed ${units.name}", flush = true)
    }

    fun preChange(seconds: Int, nextSpeed: Double? = null, units: Units) {
        if (seconds > 0) {
            val message = "Speed change in $seconds seconds"
            val nextSpeedMessage = nextSpeed?.let { " to $it ${units.name}" } ?: ""
            sayAsync(message + nextSpeedMessage)
        }
    }

    fun changeNow(newSpeed: Double, units: Units) {
        sayAsync("Change speed now to $newSpeed ${units.name}", flush = true)
    }

    fun finish() {
        sayAsync("Session complete", flush = true)
    }

    fun release() {
        tts.stop()
        tts.shutdown()
        beeper.release()
    }
}
