
package app.insidepacer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarView(
    month: YearMonth,
    onMonthChanged: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit = {},
    dayContent: @Composable (LocalDate) -> Unit = {},
    fullDayCell: (@Composable (date: LocalDate, modifier: Modifier) -> Unit)? = null
) {
    val daysInMonth = month.lengthOfMonth()
    val firstDayOfMonth = month.atDay(1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Sunday is 0

    Column {
        // Month header with navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onMonthChanged(month.minusMonths(1)) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { onMonthChanged(month.plusMonths(1)) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Days of the week header
        Row(modifier = Modifier.fillMaxWidth()) {
            val days = DayOfWeek.values()
            // Shift array so Sunday is first
            val shiftedDays = days.sliceArray(6..6) + days.sliceArray(0..5)
            for (day in shiftedDays) {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Calendar grid
        val totalCells = (daysInMonth + firstDayOfWeek + 6) / 7 * 7 // Ensure full weeks
        for (week in 0 until (totalCells / 7)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (day in 0..6) {
                    val dayIndex = week * 7 + day
                    val cellModifier = Modifier.weight(1f).aspectRatio(1f)
                    if (dayIndex >= firstDayOfWeek && dayIndex < daysInMonth + firstDayOfWeek) {
                        val dateNumber = dayIndex - firstDayOfWeek + 1
                        val date = month.atDay(dateNumber)

                        if (fullDayCell != null) {
                            fullDayCell(date, cellModifier)
                        } else {
                            Surface(
                                modifier = cellModifier.clickable { onDateSelected(date) },
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = "$dateNumber")
                                    dayContent(date)
                                }
                            }
                        }
                    } else {
                        // Empty cell for padding
                        Box(modifier = cellModifier)
                    }
                }
            }
        }
    }
}
