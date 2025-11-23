# Accessibility in InsidePacer

This document describes the accessibility features and best practices implemented in InsidePacer to ensure the app is usable by all users, including those using assistive technologies like TalkBack.

## Overview

InsidePacer is committed to providing an accessible experience for all users. The app follows Android accessibility best practices and Material Design guidelines to ensure compatibility with screen readers, switch access, and other assistive technologies.

## Accessibility Features

### TalkBack Support

All interactive UI elements include proper content descriptions and semantic properties for screen reader users:

1. **Buttons**: All buttons have descriptive labels that announce their purpose
2. **Icons**: Icon-only buttons include content descriptions
3. **State Changes**: Dynamic content includes state descriptions that update as the app state changes
4. **Forms**: Input fields have appropriate labels and hints
5. **Navigation**: Clear navigation structure with meaningful headings

### Screen Reader Announcements

The app provides context-aware announcements for:
- **Session Status**: Current workout state (running, paused, stopped)
- **Timer Updates**: Time remaining in segments
- **Speed Changes**: Current speed and upcoming changes
- **Progress**: Workout completion progress
- **Actions**: Results of user actions (session started, template saved, etc.)

### Keyboard Navigation

- All interactive elements are keyboard-navigable
- Logical tab order follows visual layout
- Focus indicators clearly show current element

### Touch Targets

- Minimum touch target size of 48dp x 48dp
- Adequate spacing between interactive elements
- Large, easy-to-tap buttons for primary actions

## Implementation Guidelines

### Content Descriptions

#### Buttons with Text
```kotlin
Button(onClick = { /* action */ }) {
    Text("Start Workout")
}
// Text is automatically announced by TalkBack
```

#### Icon Buttons
```kotlin
IconButton(onClick = { refresh() }) {
    Icon(
        Icons.Default.Refresh,
        contentDescription = "Refresh workout list"
    )
}
```

#### Stateful Elements
```kotlin
Button(
    onClick = { togglePause() },
    modifier = Modifier.semantics {
        stateDescription = if (isPaused) "Session is paused" else "Session is running"
    }
) {
    Text(if (isPaused) "Resume" else "Pause")
}
```

### Semantic Properties

Use semantic properties for complex UI elements:

```kotlin
Box(
    modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "Current speed: ${formatSpeed(speed, units)}"
        role = Role.Image  // For static information display
    }
) {
    // Speed display content
}
```

### Live Regions

For dynamic content that updates frequently:

```kotlin
Text(
    text = formatDuration(remainingSeconds),
    modifier = Modifier.semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = "Time remaining: ${formatDuration(remainingSeconds)}"
    }
)
```

### Grouping Related Content

```kotlin
Column(
    modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "Workout statistics: ${items.size} sessions, $totalTime total time"
    }
) {
    // Statistics content
}
```

## Testing Accessibility

### TalkBack Testing

1. **Enable TalkBack**: Settings → Accessibility → TalkBack
2. **Navigation**: Use swipe gestures to move between elements
3. **Activation**: Double-tap to activate focused element
4. **Verify announcements**: Ensure all content is properly announced

### Automated Testing

Use Compose UI testing to verify accessibility:

```kotlin
@Test
fun sessionControls_haveContentDescriptions() {
    composeTestRule.setContent {
        SessionControlButtons()
    }
    
    composeTestRule
        .onNodeWithContentDescription("Start workout")
        .assertExists()
        .assertIsDisplayed()
}
```

### Accessibility Scanner

Use Android's Accessibility Scanner app to identify issues:
1. Install from Google Play Store
2. Enable in Accessibility settings
3. Scan each screen for issues
4. Address identified problems

## Key Areas

### Session Screen

- **Timer display**: Announces time remaining with proper formatting
- **Speed indicator**: Announces current and target speeds
- **Control buttons**: Pause/Resume, Stop buttons with state-aware descriptions
- **Segment list**: Each segment announced with speed and duration

### History Screen

- **Session list**: Each entry announces date, duration, and completion status
- **Notes**: Session notes are properly announced
- **Action buttons**: Export, clear, refresh all have descriptions
- **Edit controls**: Note editing dialog fully accessible

### Statistics Dashboard

- **Summary cards**: Overall statistics announced as grouped content
- **Personal records**: Each record type and value announced
- **Charts/Graphs**: Alternative text descriptions for visual data
- **Refresh action**: Clear announcement of data refresh

### Template Editor

- **Segment input**: Speed and duration fields with labels
- **Add/Remove**: Clear descriptions for segment manipulation
- **Save/Cancel**: Action buttons properly labeled
- **Template name**: Input field with label and hint

### Training Programs

- **Calendar view**: Days and workouts announced by date
- **Workout assignment**: Drag-drop alternatives for screen readers
- **Program details**: Name, duration, structure announced

### Settings Screen

- **Preferences**: Each setting announced with current value
- **Units**: Speed unit selection announced
- **Voice/Beep toggles**: State announced (enabled/disabled)
- **Backup actions**: Sign in, backup, restore properly labeled

## Accessibility Checklist

When adding new features, ensure:

- [ ] All buttons and interactive elements have content descriptions
- [ ] Icon-only buttons include descriptive contentDescription
- [ ] State changes are announced with stateDescription
- [ ] Forms have proper labels and hints
- [ ] Touch targets meet minimum 48dp size
- [ ] Color is not the only means of conveying information
- [ ] Text has sufficient contrast (4.5:1 for normal text, 3:1 for large text)
- [ ] Dynamic content uses appropriate live region modes
- [ ] Custom components support accessibility services
- [ ] Tested with TalkBack enabled
- [ ] Keyboard navigation works correctly

## Common Patterns

### Loading States

```kotlin
if (isLoading) {
    CircularProgressIndicator(
        modifier = Modifier.semantics {
            contentDescription = "Loading workout data"
        }
    )
}
```

### Empty States

```kotlin
if (items.isEmpty()) {
    Text(
        "No workouts yet. Start your first workout to see it here.",
        modifier = Modifier.semantics {
            heading()  // Mark as heading for better navigation
        }
    )
}
```

### Action Confirmation

```kotlin
AlertDialog(
    onDismissRequest = { /* dismiss */ },
    title = { Text("Clear all workouts?") },
    text = { Text("This will permanently delete all workout history.") },
    confirmButton = {
        TextButton(onClick = { /* confirm */ }) {
            Text("Clear all")
        }
    },
    dismissButton = {
        TextButton(onClick = { /* dismiss */ }) {
            Text("Cancel")
        }
    }
)
// Dialog role and structure automatically handled by Material
```

## Resources

### Official Documentation

- [Android Accessibility](https://developer.android.com/guide/topics/ui/accessibility)
- [Compose Accessibility](https://developer.android.com/jetpack/compose/accessibility)
- [Material Design Accessibility](https://m3.material.io/foundations/accessible-design/overview)
- [TalkBack Guide](https://support.google.com/accessibility/android/answer/6283677)

### Testing Tools

- [Accessibility Scanner](https://play.google.com/store/apps/details?id=com.google.android.apps.accessibility.auditor)
- [TalkBack](https://play.google.com/store/apps/details?id=com.google.android.marvin.talkback)
- [Android Lint Accessibility Checks](https://developer.android.com/studio/write/lint)

### Best Practices

- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Android Accessibility Help](https://support.google.com/accessibility/android)
- [Google Accessibility](https://www.google.com/accessibility/)

## Continuous Improvement

Accessibility is an ongoing process. We regularly:
- Test with real users who rely on assistive technologies
- Update content descriptions based on user feedback
- Review and improve keyboard navigation
- Ensure new features maintain accessibility standards
- Monitor accessibility issues reported by users

## Reporting Accessibility Issues

If you encounter accessibility issues:
1. Open an issue on GitHub with the "accessibility" label
2. Describe the issue and which assistive technology you're using
3. Include steps to reproduce
4. Suggest improvements if possible

We prioritize accessibility issues and aim to address them promptly.

---

Last updated: November 2025
