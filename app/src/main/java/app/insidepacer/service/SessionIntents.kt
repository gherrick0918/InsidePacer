package app.insidepacer.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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
    val playableSegments = segments.filter { it.seconds > 0 }
    if (playableSegments.isEmpty()) return

    val segJson = json.encodeToString(ListSerializer(Segment.serializer()), playableSegments)
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
    runCatching {
        ContextCompat.startForegroundService(this, intent)
    }.getOrElse {
        startService(intent)
    }
}

fun Context.pauseSession() {
    sendBroadcast(Intent(this, SessionBroadcastReceiver::class.java).setAction(SessionService.ACTION_PAUSE))
}

fun Context.resumeSession() {
    sendBroadcast(Intent(this, SessionBroadcastReceiver::class.java).setAction(SessionService.ACTION_RESUME))
}

fun Context.stopSession() {
    sendBroadcast(Intent(this, SessionBroadcastReceiver::class.java).setAction(SessionService.ACTION_STOP))
}

fun Context.skipSessionSegment() {
    sendBroadcast(Intent(this, SessionBroadcastReceiver::class.java).setAction(SessionService.ACTION_SKIP))
}
