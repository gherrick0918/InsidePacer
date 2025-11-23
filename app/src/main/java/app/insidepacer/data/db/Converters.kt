package app.insidepacer.data.db

import androidx.room.TypeConverter
import app.insidepacer.domain.Segment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromSegmentList(value: List<Segment>): String {
        return json.encodeToString(ListSerializer(Segment.serializer()), value)
    }

    @TypeConverter
    fun toSegmentList(value: String): List<Segment> {
        return json.decodeFromString(ListSerializer(Segment.serializer()), value)
    }

    @TypeConverter
    fun fromGrid(value: List<List<String?>>): String {
        return json.encodeToString(
            ListSerializer(ListSerializer(String.serializer().nullable)),
            value
        )
    }

    @TypeConverter
    fun toGrid(value: String): List<List<String?>> {
        return json.decodeFromString(
            ListSerializer(ListSerializer(String.serializer().nullable)),
            value
        )
    }
}
