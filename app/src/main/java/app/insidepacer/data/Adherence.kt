package app.insidepacer.data

import app.insidepacer.domain.Program
import java.time.LocalDate

data class Streaks(val current: Int, val longest: Int)

fun computeStreaks(program: Program, progress: ProgramProgressRepo): Streaks {
    val start = program.startEpochDay
    val totalDays = program.weeks * program.daysPerWeek
    if (totalDays <= 0) return Streaks(0, 0)

    var cur = 0
    var best = 0
    for (i in 0 until totalDays) {
        val epoch = start + i
        val done = progress.isDone(program.id, epoch)
        if (done) {
            cur += 1
            if (cur > best) best = cur
        } else {
            cur = 0
        }
    }
    val today = LocalDate.now().toEpochDay()
    val endIndex = (today - start).toInt().coerceIn(0, totalDays)
    var tail = 0
    for (i in 0 until endIndex) {
        val epoch = start + i
        val done = progress.isDone(program.id, epoch)
        tail = if (done) tail + 1 else 0
    }
    return Streaks(current = tail, longest = best)
}

fun dayIndexFor(program: Program, epochDay: Long): Int =
    (epochDay - program.startEpochDay).toInt()

fun inRange(program: Program, idx: Int): Boolean =
    idx >= 0 && idx < program.weeks * program.daysPerWeek
