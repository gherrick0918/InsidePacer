package app.insidepacer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.insidepacer.domain.Segment
import app.insidepacer.ui.theme.Spacings
import app.insidepacer.core.formatDuration
import app.insidepacer.core.formatSpeed
import app.insidepacer.data.Units

@Composable
fun WorkoutPlan(segments: List<Segment>, currentSegment: Int, units: Units) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Workout Plan", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Spacings.small))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacings.small),
        ) {
            itemsIndexed(segments) { index, segment ->
                val backgroundColor = if (index == currentSegment) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
                val statusText = if (index == currentSegment) "Current segment" else "Segment ${index + 1}"
                val labelText = segment.label ?: statusText
                val segmentDescription = buildString {
                    append(labelText)
                    append(": ")
                    append(formatSpeed(segment.speed, units))
                    append(" for ")
                    append(formatDuration(segment.seconds))
                    segment.description?.let { desc ->
                        append(". ")
                        append(desc)
                    }
                }
                
                Box(
                    modifier = Modifier
                        .background(backgroundColor)
                        .padding(Spacings.small)
                        .semantics {
                            contentDescription = segmentDescription
                        }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        segment.label?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = formatSpeed(segment.speed, units), style = MaterialTheme.typography.bodyLarge)
                        Text(text = formatDuration(segment.seconds), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
