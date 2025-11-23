package app.insidepacer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.insidepacer.domain.Segment

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val segments: List<Segment>
)
