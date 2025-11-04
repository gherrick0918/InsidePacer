package app.insidepacer.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.insidepacer.data.Units
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CuePlayer(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts: TextToSpeech
    private val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val beeper = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    @Volatile
    private var voiceOn = true

    private val ttsInitialized = CompletableDeferred<Unit>()
    private val utteranceCompletions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val inFlight = AtomicInteger(0)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { }
            .build()
    }

    init {
        tts = TextToSpeech(ctx, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            tts.setAudioAttributes(audioAttributes)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) {
                    utteranceCompletions.remove(utteranceId)?.complete(Unit)
                    endDuck()
                }

                override fun onError(utteranceId: String) {
                    utteranceCompletions.remove(utteranceId)?.complete(Unit)
                    endDuck()
                }
            })
            ttsInitialized.complete(Unit)
        } else {
            ttsInitialized.completeExceptionally(RuntimeException("TTS initialization failed"))
        }
    }

    fun setVoiceEnabled(on: Boolean) {
        voiceOn = on
        if (!on) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            inFlight.set(0)
        }
    }

    private fun beginDuck() {
        if (!voiceOn) return
        if (inFlight.getAndIncrement() == 0) {
            audioManager.requestAudioFocus(focusRequest)
        }
    }

    private fun endDuck() {
        if (inFlight.decrementAndGet() <= 0) {
            inFlight.set(0)
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private suspend fun say(text: String, flush: Boolean) {
        ttsInitialized.await()
        if (!voiceOn) return

        val utteranceId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Unit>()
        utteranceCompletions[utteranceId] = deferred

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        beginDuck()
        tts.speak(text, queueMode, null, utteranceId)

        try {
            deferred.await()
        } catch (e: Exception) {
            utteranceCompletions.remove(utteranceId)
        }
    }

    private fun sayAsync(text: String, flush: Boolean = false) {
        if (!voiceOn || !ttsInitialized.isCompleted) return
        val utteranceId = UUID.randomUUID().toString()
        utteranceCompletions[utteranceId] = CompletableDeferred()
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        beginDuck()
        tts.speak(text, queueMode, null, utteranceId)
    }

    fun beep() {
        beeper.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
    }

    suspend fun countdown321(delayMs: Long = 1000) {
        ttsInitialized.await()
        beginDuck()
        beep(); delay(150); sayAsync("3"); delay(delayMs - 150)
        beep(); delay(150); sayAsync("2"); delay(delayMs - 150)
        beep(); delay(150); sayAsync("1"); delay(delayMs - 150)
        say("Go", flush = false) // use say to wait for completion
        endDuck()
    }

    suspend fun announceStartingSpeed(speed: Double, units: Units) {
        say("First speed is $speed ${units.name.lowercase(Locale.getDefault())}", flush = true)
    }

    fun preChange(seconds: Int, nextSpeed: Double? = null, units: Units) {
        if (seconds > 0) {
            val message = "Speed change in $seconds seconds"
            val nextSpeedMessage = nextSpeed?.let { " to $it ${units.name.lowercase(Locale.getDefault())}" } ?: ""
            sayAsync(message + nextSpeedMessage)
        }
    }

    fun changeNow(newSpeed: Double, units: Units) {
        beep()
        sayAsync("Change speed now to $newSpeed ${units.name.lowercase(Locale.getDefault())}", flush = true)
    }

    fun finish() {
        sayAsync("Session complete", flush = true)
    }

    fun release() {
        tts.stop()
        tts.shutdown()
        beeper.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
        inFlight.set(0)
    }
}
