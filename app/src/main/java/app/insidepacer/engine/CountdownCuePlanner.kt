package app.insidepacer.engine

internal class CountdownCuePlanner {
    private var currentSegmentDuration = 0

    fun onSegmentStarted(durationSeconds: Int) {
        currentSegmentDuration = durationSeconds
    }

    fun allowTick(secondsRemaining: Int, voiceOn: Boolean): Boolean {
        if (voiceOn) return false
        if (currentSegmentDuration < MIN_SEGMENT_LENGTH_FOR_TICKS) return false
        return secondsRemaining in 1..3
    }

    companion object {
        private const val MIN_SEGMENT_LENGTH_FOR_TICKS = 3
    }
}
