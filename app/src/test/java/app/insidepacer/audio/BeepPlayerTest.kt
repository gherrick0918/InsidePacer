package app.insidepacer.audio

import android.media.ToneGenerator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Basic tests for BeepPlayer
 * Note: Robolectric provides shadow implementations of Android audio classes
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BeepPlayerTest {

    @Test
    fun `BeepPlayer can be created`() {
        val beepPlayer = BeepPlayer()
        // Should not throw exception
        assert(beepPlayer != null)
    }

    @Test
    fun `playTone does not crash with standard tone`() {
        val beepPlayer = BeepPlayer()
        
        // Should not throw exception
        beepPlayer.playTone(ToneGenerator.TONE_PROP_BEEP, 100)
        
        beepPlayer.release()
    }

    @Test
    fun `playTone with different tone types`() {
        val beepPlayer = BeepPlayer()
        
        // Test various tone types
        beepPlayer.playTone(ToneGenerator.TONE_PROP_BEEP, 100)
        beepPlayer.playTone(ToneGenerator.TONE_PROP_ACK, 100)
        beepPlayer.playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100)
        
        beepPlayer.release()
    }

    @Test
    fun `playTone with different durations`() {
        val beepPlayer = BeepPlayer()
        
        beepPlayer.playTone(ToneGenerator.TONE_PROP_BEEP, 50)
        beepPlayer.playTone(ToneGenerator.TONE_PROP_BEEP, 200)
        beepPlayer.playTone(ToneGenerator.TONE_PROP_BEEP, 500)
        
        beepPlayer.release()
    }

    @Test
    fun `release can be called safely`() {
        val beepPlayer = BeepPlayer()
        
        beepPlayer.playTone(ToneGenerator.TONE_PROP_BEEP, 100)
        beepPlayer.release()
        
        // Should not throw exception
    }

    @Test
    fun `multiple plays before release`() {
        val beepPlayer = BeepPlayer()
        
        // Play multiple tones
        repeat(5) {
            beepPlayer.playTone(ToneGenerator.TONE_PROP_BEEP, 100)
        }
        
        beepPlayer.release()
    }
}
