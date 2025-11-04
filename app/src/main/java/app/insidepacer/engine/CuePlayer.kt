package app.insidepacer.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.insidepacer.data.Units
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class CuePlayer(
    ctx: Context,
    private val duckingManager: CueDuckingManager,
) : TextToSpeech.OnInitListener {
    private val tts: TextToSpeech
    private val beeper = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    @Volatile
    private var voiceOn = true

    private val ttsInitialized = CompletableDeferred<Unit>()
    private val utteranceCompletions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val speechMutex = Mutex()
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val toneAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    init {
        tts = TextToSpeech(ctx, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            tts.setAudioAttributes(speechAttributes)
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

    private suspend fun speakInternal(text: String, flush: Boolean) {
        if (!voiceOn) return
        ttsInitialized.await()

        val utteranceId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Unit>()
        utteranceCompletions[utteranceId] = deferred

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        speechMutex.withLock {
            tts.speak(text, queueMode, null, utteranceId)
        }

        try {
            deferred.await()
        } catch (e: Exception) {
            utteranceCompletions.remove(utteranceId)
        }
    }

    suspend fun beep(intervalMs: Long = 1000L) {
        duckingManager.withFocus(toneAttributes) {
            playBeep(BEEP_DURATION_MS)
            val remaining = (intervalMs - BEEP_DURATION_MS).coerceAtLeast(0L)
            if (remaining > 0) {
                delay(remaining)
            }
        }
    }

    suspend fun countdown321(delayMs: Long = 1000) {
        ttsInitialized.await()
        duckingManager.withFocus(speechAttributes) {
            playBeep(); delay(150); speakInternal("3", flush = false); delay(delayMs - 150)
            playBeep(); delay(150); speakInternal("2", flush = false); delay(delayMs - 150)
            playBeep(); delay(150); speakInternal("1", flush = false); delay(delayMs - 150)
            speakInternal("Go", flush = false)
        }
    }

    suspend fun announceStartingSpeed(speed: Double, units: Units) {
        duckingManager.withFocus(speechAttributes) {
            speakInternal(
                "First speed is $speed ${units.name.lowercase(Locale.getDefault())}",
                flush = true
            )
        }
    }

    fun preChange(seconds: Int, nextSpeed: Double? = null, units: Units) {
        if (seconds > 0) {
            val message = "Speed change in $seconds seconds"
            val nextSpeedMessage = nextSpeed?.let { " to $it ${units.name.lowercase(Locale.getDefault())}" } ?: ""
            scope.launch {
                duckingManager.withFocus(speechAttributes) {
                    speakInternal(message + nextSpeedMessage, flush = false)
                }
            }
        }
    }

    fun changeNow(newSpeed: Double, units: Units) {
        scope.launch {
            duckingManager.withFocus(speechAttributes) {
                playBeep()
                speakInternal(
                    "Change speed now to $newSpeed ${units.name.lowercase(Locale.getDefault())}",
                    flush = true
                )
            }
        }
    }

    fun finish() {
        scope.launch {
            duckingManager.withFocus(speechAttributes) {
                speakInternal("Session complete", flush = true)
            }
        }
    }

    fun release() {
        tts.stop()
        tts.shutdown()
        beeper.release()
        scope.cancel()
    }

    private suspend fun playBeep(durationMs: Int) {
        beeper.startTone(ToneGenerator.TONE_CDMA_PIP, durationMs)
        delay(durationMs.toLong())
    }

    companion object {
        private const val BEEP_DURATION_MS = 150
    }
}
