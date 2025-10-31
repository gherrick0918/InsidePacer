package app.insidepacer.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.delay
import java.util.Locale

class CuePlayer(ctx: Context){
    private val tts = TextToSpeech(ctx){ }
    init { tts.language = Locale.getDefault() }
    private fun say(s:String){ tts.speak(s, TextToSpeech.QUEUE_ADD, null, System.nanoTime().toString()) }
    suspend fun countdown321(){ say("3"); delay(600); say("2"); delay(600); say("1"); delay(600); say("Go") }
    fun preChange(){ say("Speed change in ten seconds") }
    fun changeNow(){ say("Change speed now") }
    fun finish(){ say("Session complete") }
}