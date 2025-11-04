package app.insidepacer.engine

import android.content.Context
import android.content.Intent
import app.insidepacer.data.Units
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionState
import app.insidepacer.service.SessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionScheduler(
    private val ctx: Context,
    private val cuePlayer: CuePlayer,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state

    private var job: Job? = null

    @Volatile
    private var timeRemaining = 0
    private var onFinish: ((startMs: Long, endMs: Long, elapsedSec: Int, aborted: Boolean) -> Unit)? =
        null

    fun start(
        segments: List<Segment>,
        units: Units,
        preChangeSeconds: Int = 10,
        onFinish: ((startMs: Long, endMs: Long, elapsedSec: Int, aborted: Boolean) -> Unit)? = null
    ) {
        if (_state.value.active) return

        job?.cancel()
        this.onFinish = onFinish
        job = scope.launch {
            val startMs = System.currentTimeMillis()
            var elapsed = 0
            var aborted = true

            try {
                val intent = Intent(ctx, SessionService::class.java).setAction(SessionService.ACTION_OBSERVE)
                ctx.startService(intent)

                segments.firstOrNull()?.let { cuePlayer.announceStartingSpeed(it.speed, units) }
                cuePlayer.countdown321(1000)

                var segIdx = 0
                for (seg in segments) {
                    timeRemaining = seg.seconds
                    _state.value = _state.value.copy(
                        active = true, speed = seg.speed, currentSegment = segIdx,
                        nextChangeInSec = timeRemaining, segments = segments
                    )

                    while (timeRemaining > 0) {
                        val nextSpeed = segments.getOrNull(segIdx + 1)?.speed
                        // pre-change cue once, when crossing the threshold
                        if (timeRemaining == preChangeSeconds && nextSpeed != null) {
                            cuePlayer.preChange(preChangeSeconds, nextSpeed, units)
                        }

                        if (timeRemaining <= 3) cuePlayer.beep()

                        // pause gate
                        while (_state.value.isPaused && isActive) delay(100)

                        delay(1000)
                        timeRemaining--
                        elapsed++
                        _state.value =
                            _state.value.copy(elapsedSec = elapsed, nextChangeInSec = timeRemaining)
                    }
                    segments.getOrNull(segIdx + 1)?.let { cuePlayer.changeNow(it.speed, units) }
                    segIdx++
                }
                aborted = false
                if (isActive) cuePlayer.finish()
            } finally {
                withContext(NonCancellable) {
                    val endMs = System.currentTimeMillis()
                    if (_state.value.active) { // only invoke if it was running
                        onFinish?.invoke(startMs, endMs, elapsed, aborted)
                    }
                    _state.value = SessionState()
                    val intent = Intent(ctx, SessionService::class.java)
                    ctx.stopService(intent)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun setVoiceEnabled(enabled: Boolean) {
        cuePlayer.setVoiceEnabled(enabled)
    }

    fun pause() {
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resume() {
        _state.value = _state.value.copy(isPaused = false)
    }

    fun togglePause() {
        _state.value = _state.value.copy(isPaused = !_state.value.isPaused)
    }

    fun skip() {
        if (!_state.value.active) return
        timeRemaining = 0
    }
}
