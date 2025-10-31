package app.insidepacer.engine

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

    fun start(
        segments: List<Segment>,
        onComplete: (startMillis: Long, endMillis: Long) -> Unit = { _, _ -> }
    ) {
        job?.cancel()
        _state.value = SessionState() // reset HUD
        job = scope.launch {
            cuePlayer.countdown321Aligned()
            val startMs = System.currentTimeMillis()

            for (idx in segments.indices) {
                val seg = segments[idx]
                val nextSpeed = segments.getOrNull(idx + 1)?.speed

                var t = seg.seconds
                _state.value = _state.value.copy(
                    active = true, speed = seg.speed, currentSegment = idx,
                    nextChangeInSec = t, upcomingSpeed = nextSpeed
                )

                while (t > 0) {
                    if (t == 10) cuePlayer.preChangeTo(nextSpeed)
                    if (t in 3 downTo 1) cuePlayer.beep()
                    delay(1000)
                    t--
                    _state.value = _state.value.copy(
                        elapsedSec = _state.value.elapsedSec + 1,
                        nextChangeInSec = t
                    )
                }
                if (nextSpeed != null) cuePlayer.changeNowTo(nextSpeed)
            }

            cuePlayer.changeNowTo(null) // "Session complete"
            _state.value = _state.value.copy(active = false, upcomingSpeed = null)
            onComplete(startMs, System.currentTimeMillis())
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = SessionState() // clear HUD
    }
}
