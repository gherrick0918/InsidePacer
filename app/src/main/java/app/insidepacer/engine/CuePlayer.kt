package app.insidepacer.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.insidepacer.audio.CueDuckingManager
import app.insidepacer.core.formatDuration
import app.insidepacer.core.formatSpeed
import app.insidepacer.data.Units
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CuePlayer(
    ctx: Context,
    private val duckingManager: CueDuckingManager,
) : TextToSpeech.OnInitListener {
    private val tts: TextToSpeech
    private val beeper = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ctx.getSystemService(Vibrator::class.java)
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val countdownPlanner = CountdownCuePlanner()

    @Volatile
    private var voiceOn = true
    @Volatile
    private var beepsOn = true
    @Volatile
    private var hapticsOn = false

    private val ttsInitialized = CompletableDeferred<Unit>()
    private val utteranceCompletions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val speechMutex = Mutex()
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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

    fun setBeepsEnabled(on: Boolean) {
        beepsOn = on
    }

    fun setHapticsEnabled(on: Boolean) {
        hapticsOn = on
    }

    fun onSegmentStarted(durationSeconds: Int) {
        countdownPlanner.onSegmentStarted(durationSeconds)
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

    suspend fun countdownTick(secondsRemaining: Int, intervalMs: Long = 1000L) {
        val allowTick = countdownPlanner.allowTick(secondsRemaining, voiceOn)
        val vibrate = allowTick && hapticsOn && vibrator?.hasVibrator() == true
        val playTone = allowTick && beepsOn

        if (playTone) {
            playToneCue(TICK_TONE_TYPE, TICK_DURATION_MS, HAPTIC_TICK_MS, vibrate)
        } else if (vibrate) {
            fireHaptic(HAPTIC_TICK_MS)
        }

        val consumed = if (playTone) TICK_DURATION_MS.toLong() else 0L
        val remaining = (intervalMs - consumed).coerceAtLeast(0L)
        if (remaining > 0) {
            delay(remaining)
        }
    }

    suspend fun countdown321(delayMs: Long = 1000) {
        ttsInitialized.await()
        val digits = listOf("3", "2", "1")
        for (digit in digits) {
            val vibrate = hapticsOn && vibrator?.hasVibrator() == true
            if (beepsOn) {
                playToneCue(TICK_TONE_TYPE, TICK_DURATION_MS, HAPTIC_TICK_MS, vibrate)
            } else if (vibrate) {
                fireHaptic(HAPTIC_TICK_MS)
            }
            delay(150)
            duckingManager.speakTts {
                speakInternal(digit, flush = false)
            }
            val remaining = (delayMs - 150).coerceAtLeast(0)
            if (remaining > 0) {
                delay(remaining.toLong())
            }
        }
        duckingManager.speakTts {
            speakInternal("Go", flush = false)
        }
    }

    suspend fun announceStartingSpeed(speed: Double, units: Units) {
        duckingManager.speakTts {
            val formatted = formatSpeed(speed, units)
            speakInternal("First speed is $formatted", flush = true)
        }
    }

    fun preChange(seconds: Int, nextSpeed: Double? = null, units: Units) {
        if (seconds > 0) {
            val message = "Speed change in ${formatDuration(seconds.toLong())}"
            val nextSpeedMessage = nextSpeed?.let { " to ${formatSpeed(it, units)}" } ?: ""
            scope.launch {
                duckingManager.speakTts {
                    speakInternal(message + nextSpeedMessage, flush = false)
                }
            }
        }
    }

    fun changeNow(newSpeed: Double, units: Units) {
        scope.launch {
            val vibrate = hapticsOn && vibrator?.hasVibrator() == true
            if (beepsOn) {
                playToneCue(CHIRP_TONE_TYPE, CHIRP_DURATION_MS, HAPTIC_CHIRP_MS, vibrate)
            } else if (vibrate) {
                fireHaptic(HAPTIC_CHIRP_MS)
            }

            if (voiceOn) {
                duckingManager.speakTts {
                    val formatted = formatSpeed(newSpeed, units)
                    speakInternal("Change speed now to $formatted", flush = true)
                }
            }
        }
    }

    fun finish() {
        scope.launch {
            duckingManager.speakTts {
                speakInternal("Session complete", flush = true)
            }
        }
    }

    fun release() {
        tts.stop()
        tts.shutdown()
        beeper.release()
        vibrator?.cancel()
        scope.cancel()
    }

    private suspend fun playToneCue(
        toneType: Int,
        toneDurationMs: Int,
        hapticDurationMs: Long,
        vibrate: Boolean,
    ) {
        speechMutex.withLock {
            duckingManager.playBeep {
                if (vibrate) {
                    fireHaptic(hapticDurationMs)
                }
                playTone(toneType, toneDurationMs)
            }
        }
    }

    private suspend fun playTone(toneType: Int, durationMs: Int) {
        beeper.startTone(toneType, durationMs)
        delay(durationMs.toLong())
    }

    private fun fireHaptic(durationMs: Long) {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(durationMs)
        }
    }

    companion object {
        private const val TICK_TONE_TYPE = ToneGenerator.TONE_PROP_BEEP2
        private const val CHIRP_TONE_TYPE = ToneGenerator.TONE_PROP_PROMPT
        private const val TICK_DURATION_MS = 80
        private const val CHIRP_DURATION_MS = 120
        private const val HAPTIC_TICK_MS = 25L
        private const val HAPTIC_CHIRP_MS = 30L
    }
}
