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
import java.util.UUID

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
        if (segments.isEmpty()) return
        if (_state.value.active) return

        job?.cancel()
        this.onFinish = onFinish
        val sessionId = UUID.randomUUID().toString()
        job = scope.launch {
            val startMs = System.currentTimeMillis()
            val totalDuration = segments.sumOf { it.seconds }
            var elapsed = 0
            var aborted = true

            try {
                val intent = Intent(ctx, SessionService::class.java).setAction(SessionService.ACTION_OBSERVE)
                ctx.startService(intent)

                _state.value = SessionState(
                    active = true,
                    isPaused = false,
                    elapsedSec = 0,
                    currentSegment = 0,
                    nextChangeInSec = segments.firstOrNull()?.seconds ?: 0,
                    speed = segments.firstOrNull()?.speed ?: 0.0,
                    upcomingSpeed = segments.getOrNull(1)?.speed,
                    segments = segments,
                    sessionId = sessionId,
                    sessionStartTime = startMs,
                    totalDurationSec = totalDuration,
                    remainingSec = totalDuration,
                    currentSegmentLabel = segmentLabel(0, segments, units),
                    units = units
                )

                segments.firstOrNull()?.let { cuePlayer.announceStartingSpeed(it.speed, units) }
                cuePlayer.countdown321(1000)

                var segIdx = 0
                for (seg in segments) {
                    timeRemaining = seg.seconds
                    cuePlayer.onSegmentStarted(seg.seconds)
                    val segmentStartElapsed = elapsed
                    updateState(
                        sessionId = sessionId,
                        startMs = startMs,
                        totalDuration = totalDuration,
                        elapsed = elapsed,
                        currentSegmentIndex = segIdx,
                        currentSegment = seg,
                        segments = segments,
                        units = units,
                        nextChange = timeRemaining
                    )

                    while (timeRemaining > 0 && isActive) {
                        val nextSpeed = segments.getOrNull(segIdx + 1)?.speed
                        if (timeRemaining == preChangeSeconds && nextSpeed != null) {
                            cuePlayer.preChange(preChangeSeconds, nextSpeed, units)
                        }

                        while (_state.value.isPaused && isActive) delay(100)
                        if (!isActive) break

                        if (timeRemaining <= 3) {
                            cuePlayer.countdownTick(timeRemaining)
                        } else {
                            delay(1000)
                        }

                        timeRemaining--
                        elapsed++
                        updateState(
                            sessionId = sessionId,
                            startMs = startMs,
                            totalDuration = totalDuration,
                            elapsed = elapsed,
                            currentSegmentIndex = segIdx,
                            currentSegment = seg,
                            segments = segments,
                            units = units,
                            nextChange = timeRemaining
                        )
                    }

                    if (!isActive) break

                    val elapsedInSegment = elapsed - segmentStartElapsed
                    elapsed += (seg.seconds - elapsedInSegment)

                    segments.getOrNull(segIdx + 1)?.let { cuePlayer.changeNow(it.speed, units) }
                    segIdx++
                }

                if (isActive) {
                    aborted = false
                    cuePlayer.finish()
                }

                updateState(
                    sessionId = sessionId,
                    startMs = startMs,
                    totalDuration = totalDuration,
                    elapsed = elapsed,
                    currentSegmentIndex = segments.lastIndex,
                    currentSegment = segments.lastOrNull(),
                    segments = segments,
                    units = units,
                    nextChange = 0
                )
            } finally {
                withContext(NonCancellable) {
                    val endMs = System.currentTimeMillis()
                    val currentState = _state.value
                    if (currentState.active && currentState.sessionId == sessionId) {
                        onFinish?.invoke(startMs, endMs, elapsed, aborted)
                    }
                    _state.value = SessionState()
                    val stopIntent = Intent(ctx, SessionService::class.java)
                    ctx.stopService(stopIntent)
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

    fun setBeepsEnabled(enabled: Boolean) {
        cuePlayer.setBeepsEnabled(enabled)
    }

    fun setHapticsEnabled(enabled: Boolean) {
        cuePlayer.setHapticsEnabled(enabled)
    }

    fun pause() {
        if (!_state.value.active) return
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resume() {
        if (!_state.value.active) return
        _state.value = _state.value.copy(isPaused = false)
    }

    fun togglePause() {
        if (!_state.value.active) return
        _state.value = _state.value.copy(isPaused = !_state.value.isPaused)
    }

    fun skip() {
        if (!_state.value.active) return
        timeRemaining = 0
    }

    private fun updateState(
        sessionId: String,
        startMs: Long,
        totalDuration: Int,
        elapsed: Int,
        currentSegmentIndex: Int,
        currentSegment: Segment?,
        segments: List<Segment>,
        units: Units,
        nextChange: Int
    ) {
        val remaining = (totalDuration - elapsed).coerceAtLeast(0)
        _state.value = _state.value.copy(
            sessionId = sessionId,
            active = true,
            elapsedSec = elapsed,
            currentSegment = currentSegmentIndex.coerceAtLeast(0),
            nextChangeInSec = nextChange.coerceAtLeast(0),
            speed = currentSegment?.speed ?: _state.value.speed,
            upcomingSpeed = segments.getOrNull(currentSegmentIndex + 1)?.speed,
            segments = segments,
            sessionStartTime = startMs,
            totalDurationSec = totalDuration,
            remainingSec = remaining,
            currentSegmentLabel = segmentLabel(currentSegmentIndex, segments, units),
            units = units
        )
    }

    private fun segmentLabel(index: Int, segments: List<Segment>, units: Units): String? {
        if (segments.isEmpty() || index !in segments.indices) return null
        val labelNumber = index + 1
        return "Segment $labelNumber/${segments.size}"
    }
}
