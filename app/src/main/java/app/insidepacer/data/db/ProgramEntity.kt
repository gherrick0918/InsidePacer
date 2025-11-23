package app.insidepacer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val startEpochDay: Long,
    val weeks: Int,
    val daysPerWeek: Int,
    val grid: List<List<String?>>
)
