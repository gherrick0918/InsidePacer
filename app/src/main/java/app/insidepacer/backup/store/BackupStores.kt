package app.insidepacer.backup.store

import android.content.Context
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.SessionRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.data.ProgramRepo
import app.insidepacer.data.Units
import app.insidepacer.data.Biometrics
import app.insidepacer.domain.Program
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

interface TemplateStore {
    suspend fun loadAll(): List<Template>
    suspend fun save(template: Template)
}

interface ProgramStore {
    suspend fun loadAll(): List<Program>
    suspend fun save(program: Program)
}

interface SessionStore {
    suspend fun loadAll(): List<SessionLog>
    suspend fun replaceAll(logs: List<SessionLog>)
}

interface SettingsStore {
    suspend fun read(): SettingsSnapshot
    suspend fun write(snapshot: SettingsSnapshot)
}

data class SettingsSnapshot(
    val voiceEnabled: Boolean,
    val beepEnabled: Boolean,
    val hapticsEnabled: Boolean,
    val preChangeSeconds: Int,
    val units: Units,
    val speeds: List<Double>,
    val biometrics: Biometrics?,
    val healthConnectEnabled: Boolean,
)

class RealTemplateStore(private val repo: TemplateRepo) : TemplateStore {
    override suspend fun loadAll(): List<Template> = withContext(Dispatchers.IO) { repo.loadAll() }

    override suspend fun save(template: Template) = withContext(Dispatchers.IO) {
        repo.save(template)
    }
}

class RealProgramStore(private val repo: ProgramRepo) : ProgramStore {
    override suspend fun loadAll(): List<Program> = withContext(Dispatchers.IO) { repo.loadAll() }

    override suspend fun save(program: Program) = withContext(Dispatchers.IO) {
        repo.save(program)
    }
}

class RealSessionStore(private val repo: SessionRepo) : SessionStore {
    override suspend fun loadAll(): List<SessionLog> = withContext(Dispatchers.IO) { repo.loadAll() }

    override suspend fun replaceAll(logs: List<SessionLog>) = withContext(Dispatchers.IO) {
        repo.replaceAll(logs)
    }
}

class RealSettingsStore(private val context: Context, private val repo: SettingsRepo) : SettingsStore {
    override suspend fun read(): SettingsSnapshot {
        val voice = repo.voiceEnabled.first()
        val beep = repo.beepEnabled.first()
        val haptics = repo.hapticsEnabled.first()
        val preChange = repo.preChangeSeconds.first()
        val units = repo.units.first()
        val speeds = repo.speeds.first()
        val biom = repo.biometrics.first()
        val healthConnectEnabled = repo.healthConnectEnabled.first()
        return SettingsSnapshot(
            voiceEnabled = voice,
            beepEnabled = beep,
            hapticsEnabled = haptics,
            preChangeSeconds = preChange,
            units = units,
            speeds = speeds,
            biometrics = biom,
            healthConnectEnabled = healthConnectEnabled,
        )
    }

    override suspend fun write(snapshot: SettingsSnapshot) {
        repo.setVoiceEnabled(snapshot.voiceEnabled)
        repo.setBeepEnabled(snapshot.beepEnabled)
        repo.setHapticsEnabled(snapshot.hapticsEnabled)
        repo.setPreChangeSeconds(snapshot.preChangeSeconds)
        repo.setUnits(snapshot.units)
        repo.setSpeeds(snapshot.speeds)
        repo.setBiometrics(snapshot.biometrics)
        repo.setHealthConnectEnabled(snapshot.healthConnectEnabled)
    }
}

fun createTemplateStore(context: Context): TemplateStore = RealTemplateStore(TemplateRepo(context))
fun createProgramStore(context: Context): ProgramStore = RealProgramStore(ProgramRepo(context))
fun createSessionStore(context: Context): SessionStore = RealSessionStore(SessionRepo(context))
fun createSettingsStore(context: Context): SettingsStore = RealSettingsStore(context, SettingsRepo(context))
