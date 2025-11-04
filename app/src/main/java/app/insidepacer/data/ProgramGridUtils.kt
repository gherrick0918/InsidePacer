package app.insidepacer.data

import app.insidepacer.domain.Program

/**
 * Provides safe access and updates for program grid data that may have been persisted
 * before structural changes (for example, rows shorter than the current days-per-week).
 */
fun Program.templateIdAt(week: Int, day: Int): String? =
    grid.getOrNull(week)?.getOrNull(day)

fun Program.normalizedGrid(): List<List<String?>> {
    val weekCount = weeks.coerceAtLeast(0)
    val dayCount = daysPerWeek.coerceAtLeast(0)
    return List(weekCount) { weekIndex ->
        val row = grid.getOrNull(weekIndex)
        List(dayCount) { dayIndex -> row?.getOrNull(dayIndex) }
    }
}

fun Program.withTemplateId(week: Int, day: Int, templateId: String?): Program {
    if (week !in 0 until weeks || day !in 0 until daysPerWeek) return this
    val normalized = normalizedGrid().toMutableList()
    val updatedRow = normalized[week].toMutableList().also { row ->
        if (day >= row.size) {
            // Pad the row if necessary to avoid crashes from legacy data.
            while (row.size <= day) {
                row += null
            }
        }
        row[day] = templateId
    }
    normalized[week] = updatedRow.toList()
    return copy(grid = normalized.map { it.toList() })
}
