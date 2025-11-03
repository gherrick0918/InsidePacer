package app.insidepacer.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class CuePlayer(ctx: Context){
  private val tts = TextToSpeech(ctx){ }
  @Volatile private var voiceOn = true

  init { tts.language = Locale.getDefault() }

  fun setVoiceEnabled(on: Boolean) { voiceOn = on }

  private fun say(s:String){
    if (!voiceOn) return
    tts.speak(s, TextToSpeech.QUEUE_ADD, null, System.nanoTime().toString())
  }

  suspend fun countdown321(delayMs: Long = 1000){
    // Delay handled by scheduler; we only speak the ticks.
    say("3"); kotlinx.coroutines.delay(delayMs)
    say("2"); kotlinx.coroutines.delay(delayMs)
    say("1"); kotlinx.coroutines.delay(delayMs)
    say("Go")
  }

  fun preChange(seconds: Int){ if (seconds > 0) say("Speed change in $seconds seconds") }
  fun changeNow(){ say("Change speed now") }
  fun finish(){ say("Session complete") }

  fun release(){ tts.stop(); tts.shutdown() }
}
