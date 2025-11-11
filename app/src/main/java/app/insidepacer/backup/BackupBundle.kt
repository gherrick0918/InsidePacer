package app.insidepacer.backup

import app.insidepacer.backup.store.SettingsSnapshot
import app.insidepacer.data.Biometrics
import app.insidepacer.data.Units
import app.insidepacer.domain.Program
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.Template
import kotlinx.serialization.Serializable

@Serializable
data class BackupBundle(
    val version: Int = 1,
    val exportedAtUtc: String,
    val programs: List<ProgramDto> = emptyList(),
    val templates: List<TemplateDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val segments: List<SegmentDto> = emptyList(),
    val settings: SettingsDto
)

@Serializable
data class ProgramDto(
    val id: String,
    val name: String,
    val startEpochDay: Long,
    val weeks: Int,
    val daysPerWeek: Int,
    val grid: List<List<String?>>
) {
    fun toDomain(): Program = Program(id, name, startEpochDay, weeks, daysPerWeek, grid)

    companion object {
        fun from(domain: Program): ProgramDto = ProgramDto(
            id = domain.id,
            name = domain.name,
            startEpochDay = domain.startEpochDay,
            weeks = domain.weeks,
            daysPerWeek = domain.daysPerWeek,
            grid = domain.grid
        )
    }
}

@Serializable
data class TemplateDto(
    val id: String,
    val name: String,
    val segments: List<SegmentDto>
) {
    fun toDomain(): Template = Template(id, name, segments.map { it.toDomain() })

    companion object {
        fun from(domain: Template): TemplateDto = TemplateDto(
            id = domain.id,
            name = domain.name,
            segments = domain.segments.map { SegmentDto.from(it) }
        )
    }
}

@Serializable
data class SessionDto(
    val id: String,
    val programId: String?,
    val startMillis: Long,
    val endMillis: Long,
    val totalSeconds: Int,
    val segments: List<SegmentDto>,
    val aborted: Boolean
) {
    fun toDomain(): SessionLog = SessionLog(
        id = id,
        programId = programId,
        startMillis = startMillis,
        endMillis = endMillis,
        totalSeconds = totalSeconds,
        segments = segments.map { it.toDomain() },
        aborted = aborted
    )

    companion object {
        fun from(domain: SessionLog): SessionDto = SessionDto(
            id = domain.id,
            programId = domain.programId,
            startMillis = domain.startMillis,
            endMillis = domain.endMillis,
            totalSeconds = domain.totalSeconds,
            segments = domain.segments.map { SegmentDto.from(it) },
            aborted = domain.aborted
        )
    }
}

@Serializable
data class SegmentDto(
    val speed: Double,
    val seconds: Int
) {
    fun toDomain(): Segment = Segment(speed, seconds)

    companion object {
        fun from(domain: Segment): SegmentDto = SegmentDto(domain.speed, domain.seconds)
    }
}

@Serializable
data class SettingsDto(
    val voiceEnabled: Boolean,
    val beepEnabled: Boolean,
    val hapticsEnabled: Boolean,
    val preChangeSeconds: Int,
    val units: Units,
    val speeds: List<Double> = emptyList(),
    val biometrics: BiometricsDto? = null,
    val healthConnectEnabled: Boolean = false,
) {
    fun toSnapshot(): SettingsSnapshot = SettingsSnapshot(
        voiceEnabled = voiceEnabled,
        beepEnabled = beepEnabled,
        hapticsEnabled = hapticsEnabled,
        preChangeSeconds = preChangeSeconds,
        units = units,
        speeds = speeds,
        biometrics = biometrics?.toDomain(),
        healthConnectEnabled = healthConnectEnabled,
    )

    companion object {
        fun from(snapshot: SettingsSnapshot): SettingsDto = SettingsDto(
            voiceEnabled = snapshot.voiceEnabled,
            beepEnabled = snapshot.beepEnabled,
            hapticsEnabled = snapshot.hapticsEnabled,
            preChangeSeconds = snapshot.preChangeSeconds,
            units = snapshot.units,
            speeds = snapshot.speeds,
            biometrics = snapshot.biometrics?.let { BiometricsDto.from(it) },
            healthConnectEnabled = snapshot.healthConnectEnabled,
        )
    }
}

@Serializable
data class BiometricsDto(
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val age: Int? = null
) {
    fun toDomain(): Biometrics = Biometrics(heightCm = heightCm, weightKg = weightKg, age = age)

    companion object {
        fun from(domain: Biometrics): BiometricsDto = BiometricsDto(
            heightCm = domain.heightCm,
            weightKg = domain.weightKg,
            age = domain.age
        )
    }
}
