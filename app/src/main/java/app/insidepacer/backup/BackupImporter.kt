package app.insidepacer.backup

import app.insidepacer.backup.store.ProgramStore
import app.insidepacer.backup.store.SessionStore
import app.insidepacer.backup.store.SettingsStore
import app.insidepacer.backup.store.TemplateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupImporter(
    private val templateStore: TemplateStore,
    private val programStore: ProgramStore,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore
) {
    suspend fun import(bundle: BackupBundle): RestoreReport = withContext(Dispatchers.IO) {
        val templatesUpserted = importTemplates(bundle.templates)
        val programsUpserted = importPrograms(bundle.programs)
        val sessionsInserted = importSessions(bundle.sessions)
        settingsStore.write(bundle.settings.toSnapshot())
        RestoreReport(
            templatesUpserted = templatesUpserted,
            programsUpserted = programsUpserted,
            sessionsInserted = sessionsInserted
        )
    }

    private suspend fun importTemplates(templates: List<TemplateDto>): Int {
        if (templates.isEmpty()) return 0
        val existing = templateStore.loadAll().toMutableList()
        val byId = existing.associateBy { it.id }.toMutableMap()
        val byName = existing.associateBy { it.name.lowercase() }.toMutableMap()
        var upserted = 0
        for (dto in templates) {
            val domain = dto.toDomain()
            val existingTemplate = byId[dto.id] ?: byName[domain.name.lowercase()]
            val target = if (existingTemplate != null) domain.copy(id = existingTemplate.id) else domain
            if (existingTemplate == null || existingTemplate != target) {
                templateStore.save(target)
                byId[target.id] = target
                byName[target.name.lowercase()] = target
                upserted++
            }
        }
        return upserted
    }

    private suspend fun importPrograms(programs: List<ProgramDto>): Int {
        if (programs.isEmpty()) return 0
        val existing = programStore.loadAll().toMutableList()
        val byId = existing.associateBy { it.id }.toMutableMap()
        val byNatural = existing.associateBy { it.name.lowercase() to it.startEpochDay }.toMutableMap()
        var upserted = 0
        for (dto in programs) {
            val domain = dto.toDomain()
            val naturalKey = domain.name.lowercase() to domain.startEpochDay
            val existingProgram = byId[dto.id] ?: byNatural[naturalKey]
            val target = if (existingProgram != null) domain.copy(id = existingProgram.id) else domain
            if (existingProgram == null || existingProgram != target) {
                programStore.save(target)
                byId[target.id] = target
                byNatural[naturalKey] = target
                upserted++
            }
        }
        return upserted
    }

    private suspend fun importSessions(sessions: List<SessionDto>): Int {
        if (sessions.isEmpty()) return 0
        val existing = sessionStore.loadAll()
        val byId = existing.associateBy { it.id }.toMutableMap()
        val merged = existing.toMutableList()
        var inserted = 0
        for (dto in sessions) {
            val domain = dto.toDomain()
            if (byId.containsKey(domain.id)) continue
            val duplicate = merged.firstOrNull {
                it.startMillis == domain.startMillis &&
                    it.endMillis == domain.endMillis &&
                    it.totalSeconds == domain.totalSeconds
            }
            if (duplicate != null) continue
            merged += domain
            byId[domain.id] = domain
            inserted++
        }
        if (inserted > 0) {
            merged.sortBy { it.startMillis }
            sessionStore.replaceAll(merged)
        }
        return inserted
    }
}
