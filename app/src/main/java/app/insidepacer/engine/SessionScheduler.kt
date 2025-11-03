package app.insidepacer.engine

import app.insidepacer.data.Units
import app.insidepacer.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SessionScheduler(
    private val cuePlayer: CuePlayer,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state

    private var job: Job? = null

    @Volatile
    private var paused = false

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
        job?.cancel()
        paused = false
        this.onFinish = onFinish
        job = scope.launch {
            val startMs = System.currentTimeMillis()
            var elapsed = 0
            var aborted = true

            try {
                segments.firstOrNull()?.let { cuePlayer.announceStartingSpeed(it.speed, units) }
                cuePlayer.countdown321(1000)

                var segIdx = 0
                for (seg in segments) {
                    timeRemaining = seg.seconds
                    _state.value = _state.value.copy(
                        active = true, speed = seg.speed, currentSegment = segIdx,
                        nextChangeInSec = timeRemaining
                    )

                    while (timeRemaining > 0) {
                        val nextSpeed = segments.getOrNull(segIdx + 1)?.speed
                        // pre-change cue once, when crossing the threshold
                        if (timeRemaining == preChangeSeconds && nextSpeed != null) {
                            cuePlayer.preChange(preChangeSeconds, nextSpeed, units)
                        }

                        if (timeRemaining <= 3) cuePlayer.beep()

                        // pause gate
                        while (paused && isActive) delay(100)

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
                    _state.value = _state.value.copy(active = false)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun togglePause() {
        paused = !paused
    }

    fun skip() {
        if (!_state.value.active) return
        timeRemaining = 0
    }
}
