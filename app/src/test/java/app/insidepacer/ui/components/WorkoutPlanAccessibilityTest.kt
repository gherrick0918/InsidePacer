package app.insidepacer.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.insidepacer.data.Units
import app.insidepacer.domain.Segment
import org.junit.Rule
import org.junit.Test

class WorkoutPlanAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun workoutPlan_hasAccessibleContentDescriptions() {
        val segments = listOf(
            Segment(speed = 2.0, seconds = 300),
            Segment(speed = 4.0, seconds = 120),
            Segment(speed = 2.5, seconds = 90)
        )

        composeRule.setContent {
            WorkoutPlan(
                segments = segments,
                currentSegment = 0,
                units = Units.MPH
            )
        }

        // Verify the current segment has proper content description
        composeRule.onNode(
            hasContentDescription("Current segment: 2.0 mph for 5:00")
        ).assertExists()
    }

    @Test
    fun workoutPlan_withSegmentLabels_hasEnhancedDescriptions() {
        val segments = listOf(
            Segment(speed = 2.0, seconds = 300, label = "Warm-up"),
            Segment(speed = 4.0, seconds = 120, label = "Sprint"),
            Segment(speed = 2.5, seconds = 90, label = "Recovery")
        )

        composeRule.setContent {
            WorkoutPlan(
                segments = segments,
                currentSegment = 1,
                units = Units.MPH
            )
        }

        // Verify labeled segment has proper content description
        composeRule.onNode(
            hasContentDescription("Sprint: 4.0 mph for 2:00")
        ).assertExists()
    }

    @Test
    fun workoutPlan_withLabelAndDescription_includesFullContext() {
        val segments = listOf(
            Segment(
                speed = 4.5,
                seconds = 60,
                label = "Sprint",
                description = "Max effort, maintain form"
            )
        )

        composeRule.setContent {
            WorkoutPlan(
                segments = segments,
                currentSegment = 0,
                units = Units.MPH
            )
        }

        // Verify full description is accessible
        composeRule.onNode(
            hasContentDescription("Sprint: 4.5 mph for 1:00. Max effort, maintain form")
        ).assertExists()
    }

    @Test
    fun workoutPlan_displaysLabelsVisually() {
        val segments = listOf(
            Segment(speed = 2.0, seconds = 300, label = "Warm-up")
        )

        composeRule.setContent {
            WorkoutPlan(
                segments = segments,
                currentSegment = 0,
                units = Units.MPH
            )
        }

        // Verify label is displayed
        composeRule.onNodeWithText("Warm-up").assertIsDisplayed()
    }

    @Test
    fun workoutPlan_multipleSegments_eachHasUniqueDescription() {
        val segments = listOf(
            Segment(speed = 2.0, seconds = 300, label = "Warm-up"),
            Segment(speed = 4.0, seconds = 120, label = "Work"),
            Segment(speed = 2.5, seconds = 90, label = "Recovery")
        )

        composeRule.setContent {
            WorkoutPlan(
                segments = segments,
                currentSegment = 0,
                units = Units.MPH
            )
        }

        // Verify all segments have proper descriptions
        composeRule.onNode(
            hasContentDescription("Warm-up: 2.0 mph for 5:00")
        ).assertExists()

        composeRule.onNode(
            hasContentDescription("Work: 4.0 mph for 2:00")
        ).assertExists()

        composeRule.onNode(
            hasContentDescription("Recovery: 2.5 mph for 1:30")
        ).assertExists()
    }
}
