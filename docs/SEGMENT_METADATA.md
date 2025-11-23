# Segment Metadata Feature

## Overview

The Segment Metadata feature enhances workout segments with optional labels and descriptions, providing better context and improving user experience, especially for accessibility and workout clarity.

## Features

### Segment Labels

Segments can now include optional labels to identify their purpose:
- **Warm-up**: Initial low-intensity segments
- **Work**: High-intensity intervals
- **Recovery**: Rest or low-intensity periods between work segments
- **Cool-down**: Final recovery segments
- **Custom labels**: Any descriptive text

### Segment Descriptions

Optional descriptions provide additional context:
- Exercise instructions
- Form cues
- Intensity notes
- Terrain or incline information

## Data Model

### Segment Class

```kotlin
@Serializable
@Parcelize
data class Segment(
    val speed: Double,           // Speed in MPH
    val seconds: Int,            // Duration in seconds
    val label: String? = null,   // Optional label (e.g., "Warm-up", "Sprint")
    val description: String? = null  // Optional description
) : Parcelable
```

### Example Usage

```kotlin
// Basic segment (backward compatible)
val basicSegment = Segment(speed = 2.5, seconds = 300)

// Segment with label
val warmupSegment = Segment(
    speed = 2.0,
    seconds = 300,
    label = "Warm-up"
)

// Segment with label and description
val sprintSegment = Segment(
    speed = 5.0,
    seconds = 60,
    label = "Sprint",
    description = "Max effort, maintain good form"
)

// Complete workout example
val workoutSegments = listOf(
    Segment(2.0, 300, "Warm-up", "Easy pace to get ready"),
    Segment(4.0, 120, "Work", "Push hard, 80% effort"),
    Segment(2.5, 90, "Recovery", "Catch your breath"),
    Segment(4.0, 120, "Work", "Second interval, maintain pace"),
    Segment(2.5, 90, "Recovery", "Active rest"),
    Segment(4.0, 120, "Work", "Final push, give it all"),
    Segment(2.0, 300, "Cool-down", "Slow down gradually")
)
```

## UI Integration

### Workout Plan Display

The `WorkoutPlan` composable automatically displays segment labels when present:
- Labels appear above the speed/duration info
- Labels use smaller text with subdued color
- Labels improve visual organization of workout structure

### Accessibility

Segment metadata significantly enhances accessibility:

**Without metadata:**
```
"Segment 1: 2.0 mph for 5:00"
```

**With metadata:**
```
"Warm-up: 2.0 mph for 5:00. Easy pace to get ready"
```

This provides context for screen reader users, making workouts more understandable without visual cues.

### TalkBack Announcements

The enhanced `contentDescription` includes:
1. Segment label (or position if no label)
2. Speed and duration
3. Description (if present)

Example announcement:
> "Sprint: 5.0 mph for 1:00. Max effort, maintain good form"

## Backward Compatibility

The feature is fully backward compatible:
- `label` and `description` are optional with `null` defaults
- Existing segments without metadata work unchanged
- Database migrations handle nullable fields automatically
- CSV export includes new columns (empty for old data)

## Use Cases

### Training Programs

Labels help organize structured workouts:
```kotlin
val intervalWorkout = listOf(
    Segment(2.0, 300, "Warm-up"),
    Segment(4.5, 60, "Interval 1"),
    Segment(2.5, 90, "Rest"),
    Segment(4.5, 60, "Interval 2"),
    Segment(2.5, 90, "Rest"),
    Segment(4.5, 60, "Interval 3"),
    Segment(2.0, 300, "Cool-down")
)
```

### Guided Workouts

Descriptions provide coaching cues:
```kotlin
val guidedRun = listOf(
    Segment(2.0, 300, "Warm-up", "Start slow, loosen up"),
    Segment(3.5, 600, "Base pace", "Comfortable, conversational pace"),
    Segment(4.5, 120, "Tempo", "Comfortably hard, focus on breathing"),
    Segment(3.0, 180, "Recovery", "Let heart rate come down"),
    Segment(2.0, 300, "Cool-down", "Gradual slowdown, stretch after")
)
```

### Hill Training

Labels and descriptions for terrain:
```kotlin
val hillWorkout = listOf(
    Segment(2.0, 300, "Warm-up", "Flat terrain"),
    Segment(3.0, 180, "Hill climb", "Increase incline to 5%"),
    Segment(2.5, 120, "Recovery", "Return to flat"),
    Segment(3.0, 180, "Hill climb", "Increase incline to 5%"),
    Segment(2.0, 300, "Cool-down", "Flat terrain")
)
```

## Implementation Details

### Database Storage

The segment metadata is serialized as part of the segment JSON:
```json
{
  "speed": 2.0,
  "seconds": 300,
  "label": "Warm-up",
  "description": "Easy pace to get ready"
}
```

Room database stores this in the existing TEXT column using JSON converters.

### CSV Export

CSV export includes new columns:
- `segment_label`: Segment label (empty if null)
- `segment_description`: Segment description (empty if null)

Example CSV:
```csv
session_id,segment_index,speed_mph,duration_seconds,segment_label,segment_description
abc123,0,2.0,300,Warm-up,Easy pace to get ready
abc123,1,4.0,120,Work,Push hard 80% effort
abc123,2,2.5,90,Recovery,Catch your breath
```

### Backup/Restore

Segment metadata is included in backup files:
- Labels and descriptions are preserved
- Encryption covers all segment data
- Restore maintains metadata integrity

## Future Enhancements

Potential improvements for segment metadata:

1. **Predefined Labels**: Common label templates (Warm-up, Work, Recovery, Cool-down, Sprint, etc.)
2. **Label Library**: User-created label favorites
3. **Auto-labeling**: AI/heuristic-based automatic label suggestions
4. **Rich Descriptions**: Markdown or formatted text support
5. **Voice Descriptions**: Text-to-speech announcements during workout
6. **Visual Indicators**: Color coding or icons based on labels
7. **Search/Filter**: Find workouts by segment labels
8. **Statistics by Label**: Track performance across labeled segment types

## Testing

### Unit Tests

Test segment creation and serialization:
```kotlin
@Test
fun segment_withMetadata_serializes() {
    val segment = Segment(
        speed = 3.0,
        seconds = 120,
        label = "Work",
        description = "Hard effort"
    )
    
    // Test serialization
    val json = Json.encodeToString(segment)
    val deserialized = Json.decodeFromString<Segment>(json)
    
    assertEquals(segment, deserialized)
}

@Test
fun segment_withoutMetadata_isBackwardCompatible() {
    val segment = Segment(speed = 2.5, seconds = 300)
    
    assertNull(segment.label)
    assertNull(segment.description)
}
```

### UI Tests

Test label display:
```kotlin
@Test
fun workoutPlan_displaysSegmentLabels() {
    composeTestRule.setContent {
        WorkoutPlan(
            segments = listOf(
                Segment(2.0, 300, "Warm-up"),
                Segment(4.0, 60, "Sprint")
            ),
            currentSegment = 0,
            units = Units.MPH
        )
    }
    
    composeTestRule
        .onNodeWithText("Warm-up")
        .assertExists()
        .assertIsDisplayed()
}
```

### Accessibility Tests

Verify content descriptions:
```kotlin
@Test
fun segment_withMetadata_hasAccessibleDescription() {
    composeTestRule.setContent {
        WorkoutPlan(
            segments = listOf(
                Segment(3.0, 120, "Work", "Push hard")
            ),
            currentSegment = 0,
            units = Units.MPH
        )
    }
    
    composeTestRule
        .onNodeWithContentDescription("Work: 3.0 mph for 2:00. Push hard")
        .assertExists()
}
```

## Best Practices

### Label Guidelines

- **Keep labels concise**: 1-3 words ideal
- **Be consistent**: Use same labels for similar segments
- **Use common terms**: Warm-up, Work, Recovery, Cool-down
- **Consider accessibility**: Labels should be meaningful when heard

### Description Guidelines

- **Be specific but brief**: 5-10 words ideal
- **Focus on execution**: Form cues, effort level, or technique
- **Avoid redundancy**: Don't repeat what's obvious from label/speed
- **Consider voice**: Descriptions may be read aloud by TalkBack

### Template Organization

Group related segments:
```kotlin
// Clear progression with labels
val progressiveRun = listOf(
    Segment(2.0, 300, "Warm-up"),
    Segment(3.0, 600, "Base"),
    Segment(3.5, 300, "Moderate"),
    Segment(4.0, 180, "Hard"),
    Segment(2.5, 300, "Cool-down")
)
```

## Documentation References

- [Domain Models](../ARCHITECTURE.md#domain-layer) - Segment data model
- [Accessibility](ACCESSIBILITY.md) - Accessibility guidelines
- [CSV Export](../app/src/main/java/app/insidepacer/csv/CsvWriter.kt) - Export implementation

---

Last updated: November 2025
