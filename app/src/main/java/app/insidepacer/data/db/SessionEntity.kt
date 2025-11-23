package app.insidepacer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.insidepacer.domain.Segment

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val programId: String?,
    val startMillis: Long,
    val endMillis: Long,
    val totalSeconds: Int,
    val segments: List<Segment>,
    val aborted: Boolean,
    val notes: String? = null
)
