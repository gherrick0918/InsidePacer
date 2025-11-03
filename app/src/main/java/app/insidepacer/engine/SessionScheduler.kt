package app.insidepacer.engine

import app.insidepacer.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.max

class SessionScheduler(
  private val cuePlayer: CuePlayer,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
){
  private val _state = MutableStateFlow(SessionState())
  val state: StateFlow<SessionState> = _state

  private var job: Job? = null
  @Volatile private var paused = false
  private var onFinish: ((Long, Long, Int, Boolean) -> Unit)? = null

  fun start(
    segments: List<Segment>,
    preChangeSeconds: Int = 10,
    onFinish: ((startMs: Long, endMs: Long, elapsedSec: Int, aborted: Boolean) -> Unit)? = null
  ){
    job?.cancel()
    paused = false
    this.onFinish = onFinish
    job = scope.launch {
      val startMs = System.currentTimeMillis()
      var elapsed = 0
      cuePlayer.countdown321(1000)

      var segIdx = 0
      loop@ for (seg in segments){
        var t = seg.seconds
        _state.value = _state.value.copy(
          active = true, speed = seg.speed, currentSegment = segIdx,
          nextChangeInSec = t
        )

        while (t > 0){
          // pre-change cue once, when crossing the threshold
          if (t == preChangeSeconds) cuePlayer.preChange(preChangeSeconds)

          // pause gate
          while (paused && isActive) delay(100)

          delay(1000)
          t--
          elapsed++
          _state.value = _state.value.copy(elapsedSec = elapsed, nextChangeInSec = t)

          if (!isActive) break@loop
        }
        if (!isActive) break@loop
        cuePlayer.changeNow()
        segIdx++
      }

      if (isActive) cuePlayer.finish()
      val endMs = System.currentTimeMillis()
      val aborted = !isActive && _state.value.active // stopped mid-run
      _state.value = _state.value.copy(active = false)
      onFinish?.invoke(startMs, endMs, elapsed, aborted)
    }
  }

  fun stop(){ job?.cancel(); _state.value = _state.value.copy(active=false) }
  fun pause(){ paused = true }
  fun resume(){ paused = false }
  fun togglePause(){ paused = !paused }

  fun skipToNext(segments: List<Segment>){
    if (!_state.value.active) return
    // force next change
    _state.value = _state.value.copy(nextChangeInSec = 0)
  }

  fun skipToPrev(){
    // purely UI level; scheduler runs forward only. For simplicity we just set nextChange to 0;
    // if you want true "previous", model segments index and restart current with remaining time.
    _state.value = _state.value.copy(nextChangeInSec = 0)
  }
}
