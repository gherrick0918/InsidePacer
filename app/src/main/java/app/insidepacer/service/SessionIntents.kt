package app.insidepacer.service

import android.content.Context
import android.content.Intent
import app.insidepacer.data.Units
import app.insidepacer.domain.Segment
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val json = Json { prettyPrint = false }

fun Context.startSessionService(
    segments: List<Segment>,
    units: Units,
    preChange: Int,
    voiceOn: Boolean,
    programId: String? = null,
    epochDay: Long? = null
) {
    val segJson = json.encodeToString(ListSerializer(Segment.serializer()), segments)
    val intent = Intent(this, SessionService::class.java)
        .setAction(SessionService.ACTION_START)
        .putExtra(SessionService.EXTRA_SEGMENTS_JSON, segJson)
        .putExtra(SessionService.EXTRA_PRECHANGE_SEC, preChange)
        .putExtra(SessionService.EXTRA_VOICE, voiceOn)
        .putExtra(SessionService.EXTRA_UNITS, units.name)
        .putExtra(SessionService.EXTRA_PROGRAM_ID, programId)
    if (epochDay != null) {
        intent.putExtra(SessionService.EXTRA_EPOCH_DAY, epochDay)
    }
    startService(intent)
}

fun Context.pauseSession() {
    startService(Intent(this, SessionService::class.java).setAction(SessionService.ACTION_PAUSE))
}

fun Context.resumeSession() {
    startService(Intent(this, SessionService::class.java).setAction(SessionService.ACTION_RESUME))
}

fun Context.stopSession() {
    startService(Intent(this, SessionService::class.java).setAction(SessionService.ACTION_STOP))
}
