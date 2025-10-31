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
    private var plan: List<Segment> = emptyList()
    private var startMs: Long = 0L
    private var onFinish: ((startMs: Long, endMs: Long, elapsedSec: Int, aborted: Boolean) -> Unit)? = null

    fun start(
        segments: List<Segment>,
        onComplete: (startMillis: Long, endMillis: Long, elapsedSec: Int, aborted: Boolean) -> Unit = { _, _, _, _ -> }
    ) {
        job?.cancel()
        _state.value = SessionState() // reset HUD
        plan = segments
        onFinish = onComplete
        job = scope.launch {
            cuePlayer.countdown321Aligned()
            startMs = System.currentTimeMillis()

            for (idx in plan.indices) {
                val seg = plan[idx]
                val nextSpeed = plan.getOrNull(idx + 1)?.speed

                var t = seg.seconds
                _state.value = _state.value.copy(
                    active = true,
                    speed = seg.speed,
                    currentSegment = idx,
                    nextChangeInSec = t,
                    upcomingSpeed = nextSpeed
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

            cuePlayer.changeNowTo(null) // “Session complete”
            _state.value = _state.value.copy(active = false, upcomingSpeed = null)
            onFinish?.invoke(startMs, System.currentTimeMillis(), _state.value.elapsedSec, /*aborted=*/false)
        }
    }

    fun stop() {
        val endMs = System.currentTimeMillis()
        job?.cancel()
        val elapsed = _state.value.elapsedSec
        _state.value = SessionState() // clear HUD
        onFinish?.invoke(startMs, endMs, elapsed, /*aborted=*/true)
    }
}
