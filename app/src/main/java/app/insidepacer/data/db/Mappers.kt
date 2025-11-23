package app.insidepacer.data.db

import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.Template
import app.insidepacer.domain.Program

// SessionEntity <-> SessionLog mappers
fun SessionEntity.toDomain(): SessionLog {
    return SessionLog(
        id = id,
        programId = programId,
        startMillis = startMillis,
        endMillis = endMillis,
        totalSeconds = totalSeconds,
        segments = segments,
        aborted = aborted
    )
}

fun SessionLog.toEntity(): SessionEntity {
    return SessionEntity(
        id = id,
        programId = programId,
        startMillis = startMillis,
        endMillis = endMillis,
        totalSeconds = totalSeconds,
        segments = segments,
        aborted = aborted
    )
}

// TemplateEntity <-> Template mappers
fun TemplateEntity.toDomain(): Template {
    return Template(
        id = id,
        name = name,
        segments = segments
    )
}

fun Template.toEntity(): TemplateEntity {
    return TemplateEntity(
        id = id,
        name = name,
        segments = segments
    )
}

// ProgramEntity <-> Program mappers
fun ProgramEntity.toDomain(): Program {
    return Program(
        id = id,
        name = name,
        startEpochDay = startEpochDay,
        weeks = weeks,
        daysPerWeek = daysPerWeek,
        grid = grid
    )
}

fun Program.toEntity(): ProgramEntity {
    return ProgramEntity(
        id = id,
        name = name,
        startEpochDay = startEpochDay,
        weeks = weeks,
        daysPerWeek = daysPerWeek,
        grid = grid
    )
}
