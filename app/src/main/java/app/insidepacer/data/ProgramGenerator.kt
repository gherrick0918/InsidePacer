package app.insidepacer.data

import app.insidepacer.domain.Program
import app.insidepacer.domain.Segment
import app.insidepacer.domain.Template
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ProgramGenerator(
    private val templateRepo: TemplateRepo,
    private val programRepo: ProgramRepo
) {
    data class Input(
        val startEpochDay: Long,
        val daysPerWeek: Int,        // 2..7
        val sessionMinMin: Int,
        val sessionMaxMin: Int,
        val level: String,           // "Beginner" | "Intermediate"
        val speeds: List<Double>,    // user-saved
        val age: Int,
        val heightCm: Int,
        val weightKg: Double
    )

    data class Output(val program: Program, val templates: List<Template>)

    suspend fun generate(
        name: String,
        weeks: Int,
        inx: Input,
        overwriteProgramId: String? = null,
        recalculate: Boolean = false
    ): Output {
        require(inx.speeds.isNotEmpty()) { "At least one speed is required" }

        val speeds = inx.speeds.sorted()
        val start = inx.startEpochDay
        val days = inx.daysPerWeek.coerceIn(2, 7)
        val minMin = min(inx.sessionMinMin, inx.sessionMaxMin)
        val maxMin = max(inx.sessionMinMin, inx.sessionMaxMin)
        val weeksClamped = weeks.coerceIn(1, 26)

        val bmi = run {
            val hMeters = inx.heightCm.toDouble() / 100.0
            if (hMeters > 0) inx.weightKg / (hMeters * hMeters) else 0.0
        }
        val risk = (inx.age >= 55) || (bmi >= 30.0)
        val capFrac = if (risk) 0.70 else 1.0
        val capIdx = max(0, (capFrac * (speeds.size - 1)).toInt())

        val baseIdx = 0
        val midIdx = max(0, (capIdx * 0.5).toInt())
        val hiIdx = capIdx
        val base = speeds[baseIdx]
        val mid = speeds[midIdx]
        val high = speeds[hiIdx]

        val grid = MutableList(weeksClamped) { MutableList<String?>(7) { null } }
        val templates = mutableListOf<Template>()

        val todayEpoch = LocalDate.now().toEpochDay()
        val existingProgram = overwriteProgramId?.let { programRepo.get(it) }

        for (w in 0 until weeksClamped) {
            val weekProgress = if (weeksClamped <= 1) 0.0 else w.toDouble() / (weeksClamped - 1)
            val targetMin = minMin + (maxMin - minMin) * weekProgress
            val trainingDays = selectDays(days)
            var trainingDayIndex = 0

            for (d in 0 until 7) {
                val dayEpoch = start + (w * 7) + d
                if (recalculate && existingProgram != null && dayEpoch < todayEpoch && w < existingProgram.weeks && d < 7) {
                    if (d < (existingProgram.grid.getOrNull(w)?.size ?: 0)) {
                        grid[w][d] = existingProgram.grid[w][d]
                    }
                    continue
                }

                if (d !in trainingDays) {
                    grid[w][d] = null
                    continue
                }

                val kind = selectKind(level = inx.level, dayOrder = trainingDayIndex, daysPerWeek = days)
                val durationMinutes = clampToRange(
                    when (kind) {
                        Kind.EASY -> targetMin * 0.8
                        Kind.TEMPO -> targetMin
                        Kind.INTERVAL -> targetMin
                        Kind.RECOVERY -> max(15.0, targetMin * 0.6)
                        Kind.LONG -> min(maxMin.toDouble(), targetMin * 1.2)
                    },
                    minMin.toDouble(),
                    maxMin.toDouble()
                )

                val segments = when (kind) {
                    Kind.EASY -> easyWalk(durationMinutes, base, mid)
                    Kind.TEMPO -> tempoWalk(durationMinutes, base, mid)
                    Kind.INTERVAL -> intervals(durationMinutes, base, mid, high, inx.level)
                    Kind.RECOVERY -> recoveryWalk(durationMinutes, base)
                    Kind.LONG -> longEasy(durationMinutes, base, mid)
                }

                val templateName = "W${w + 1} D${trainingDayIndex + 1} ${kind.displayName}"
                val template = templateRepo.create(templateName, segments)
                templates += template
                grid[w][d] = template.id
                trainingDayIndex++
            }
        }

        val program = if (overwriteProgramId != null) {
            val existing = programRepo.get(overwriteProgramId)
            if (existing != null) {
                val updated = existing.copy(
                    name = name.ifBlank { existing.name },
                    startEpochDay = start,
                    weeks = weeksClamped,
                    daysPerWeek = 7, // Always 7 days a week
                    grid = grid.map { it.toList() }
                )
                programRepo.save(updated)
                updated
            } else {
                val created = programRepo.create(name.ifBlank { "Generated Plan" }, start, weeksClamped, 7)
                    .copy(grid = grid.map { it.toList() })
                programRepo.save(created)
                created
            }
        } else {
            val created = programRepo.create(name.ifBlank { "Generated Plan" }, start, weeksClamped, 7)
                .copy(grid = grid.map { it.toList() })
            programRepo.save(created)
            created
        }

        return Output(program, templates)
    }

    private fun selectDays(daysPerWeek: Int): List<Int> {
        if (daysPerWeek >= 7) return (0..6).toList()
        // Simple random picker. A more sophisticated version could try to space them out.
        val allDays = (0..6).toMutableList()
        allDays.shuffle()
        return allDays.take(daysPerWeek).sorted()
    }

    private fun clampToRange(value: Double, minValue: Double, maxValue: Double): Double {
        return value.coerceIn(minValue, maxValue)
    }

    private fun selectKind(level: String, dayOrder: Int, daysPerWeek: Int): Kind {
        val pattern = when (daysPerWeek) {
            2 -> listOf(Kind.INTERVAL, Kind.LONG)
            3 -> listOf(Kind.INTERVAL, Kind.EASY, Kind.LONG)
            4 -> listOf(Kind.INTERVAL, Kind.EASY, Kind.TEMPO, Kind.LONG)
            5 -> listOf(Kind.INTERVAL, Kind.EASY, Kind.TEMPO, Kind.EASY, Kind.LONG)
            6 -> listOf(Kind.INTERVAL, Kind.EASY, Kind.TEMPO, Kind.EASY, Kind.INTERVAL, Kind.LONG)
            7 -> listOf(Kind.INTERVAL, Kind.EASY, Kind.TEMPO, Kind.EASY, Kind.INTERVAL, Kind.LONG, Kind.RECOVERY)
            else -> listOf(Kind.INTERVAL, Kind.LONG)
        }
        val kind = pattern[dayOrder % pattern.size]
        return if (level.equals("Intermediate", ignoreCase = true)) kind
        else if (kind == Kind.INTERVAL) Kind.TEMPO else kind
    }

    private fun easyWalk(mins: Double, base: Double, mid: Double): List<Segment> {
        return compose(
            totalMinutes = mins,
            warm = 5.0 to base,
            blocks = listOf((mins - 8.0).coerceAtLeast(5.0) to mid),
            cool = 3.0 to base
        )
    }

    private fun tempoWalk(mins: Double, base: Double, mid: Double): List<Segment> {
        return compose(
            totalMinutes = mins,
            warm = 5.0 to base,
            blocks = listOf((mins - 8.0).coerceAtLeast(8.0) to mid),
            cool = 3.0 to base
        )
    }

    private fun intervals(mins: Double, base: Double, mid: Double, high: Double, level: String): List<Segment> {
        val workSpeed = if (level.equals("Intermediate", ignoreCase = true)) high else mid
        val block = listOf(1.0 to workSpeed, 1.5 to base)
        return compose(mins, warm = 5.0 to base, blocks = block, cool = 3.0 to base)
    }

    private fun recoveryWalk(mins: Double, base: Double): List<Segment> {
        return compose(
            totalMinutes = mins,
            warm = 3.0 to base,
            blocks = listOf((mins - 6.0).coerceAtLeast(6.0) to base),
            cool = 3.0 to base
        )
    }

    private fun longEasy(mins: Double, base: Double, mid: Double): List<Segment> {
        return compose(
            totalMinutes = mins,
            warm = 5.0 to base,
            blocks = listOf((mins - 10.0).coerceAtLeast(10.0) to mid),
            cool = 5.0 to base
        )
    }

    private fun compose(
        totalMinutes: Double,
        warm: Pair<Double, Double>,
        blocks: List<Pair<Double, Double>>,
        cool: Pair<Double, Double>
    ): List<Segment> {
        val segments = mutableListOf<Segment>()

        fun append(minutes: Double, speed: Double) {
            if (minutes <= 0.0) return
            val seconds = max(30, (minutes * 60).roundToInt())
            if (segments.isNotEmpty() && segments.last().speed == speed) {
                val last = segments.removeAt(segments.lastIndex)
                segments += last.copy(seconds = last.seconds + seconds)
            } else {
                segments += Segment(speed, seconds)
            }
        }

        append(warm.first, warm.second)
        var remaining = totalMinutes - warm.first - cool.first
        if (remaining < 0.0) remaining = 0.0

        if (remaining > 0.0) {
            val blockDuration = blocks.sumOf { it.first }
            if (blockDuration <= 0.0) {
                append(remaining, blocks.firstOrNull()?.second ?: warm.second)
            } else {
                var guard = 0
                while (remaining > 0.01 && guard < 200) {
                    for ((mins, speed) in blocks) {
                        val take = min(mins, remaining)
                        append(take, speed)
                        remaining -= take
                        if (remaining <= 0.01) break
                    }
                    guard++
                }
            }
        }

        append(cool.first, cool.second)

        val totalSeconds = segments.sumOf { it.seconds }
        val targetSeconds = max(30, (totalMinutes * 60).roundToInt())
        val diff = targetSeconds - totalSeconds
        if (diff != 0 && segments.isNotEmpty()) {
            val last = segments.removeAt(segments.lastIndex)
            segments += last.copy(seconds = max(30, last.seconds + diff))
        }

        return segments
    }

    private enum class Kind(val displayName: String) {
        EASY("Easy"),
        TEMPO("Tempo"),
        INTERVAL("Intervals"),
        RECOVERY("Recovery"),
        LONG("Long")
    }
}
