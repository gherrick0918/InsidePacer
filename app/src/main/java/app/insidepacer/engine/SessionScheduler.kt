package app.insidepacer.engine

import app.insidepacer.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SessionScheduler(
    private val cuePlayer: CuePlayer,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
){
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state
    private var job: Job? = null

    fun start(segments: List<Segment>){
        job?.cancel()
        _state.value = SessionState(active = false) // clear previous run
        job = scope.launch {
            cuePlayer.countdown321()
            var segIdx = 0
            for (seg in segments){
                var t = seg.seconds
                _state.value = _state.value.copy(speed = seg.speed, currentSegment = segIdx, nextChangeInSec = t, active = true)
                while (t > 0){
                    if (t == 10) cuePlayer.preChange()
                    delay(1000)
                    t--
                    _state.value = _state.value.copy(elapsedSec = _state.value.elapsedSec + 1, nextChangeInSec = t)
                }
                cuePlayer.changeNow()
                segIdx++
            }
            cuePlayer.finish()
            _state.value = _state.value.copy(active = false)
        }
    }
    fun stop()
    {
        job?.cancel();
        _state.value = SessionState(active = false)
    }
}
