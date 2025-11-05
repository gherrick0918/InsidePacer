package app.insidepacer.csv

import app.insidepacer.core.speedUnitToken
import app.insidepacer.data.Units

object CsvFields {
    const val SESSION_ID = "id"
    const val SESSION_START = "start"
    const val SESSION_END = "end"
    const val TOTAL_DURATION_HMS = "total_duration_hms"
    const val SEGMENTS_COUNT = "segments_count"
    const val ABORTED = "aborted"
    const val UNITS = "units"
    const val DURATION_HMS = "duration_hms"
    const val NOTES = "notes"

    const val SEGMENT_SESSION_ID = "session_id"
    const val SEGMENT_INDEX = "index"
    const val PRE_CHANGE_WARN_SEC = "pre_change_warn_sec"

    const val PROGRAM_DATE = "date"
    const val PROGRAM_WEEK = "week"
    const val PROGRAM_DAY = "day"
    const val PROGRAM_TEMPLATE = "template"
    const val PROGRAM_REST = "rest"
    const val PROGRAM_DONE = "done"
    const val PROGRAM_NAME = "program_name"
    const val TEMPLATE_NAME = "template_name"
    fun speed(units: Units): String = "speed_${speedUnitToken(units)}"
    fun avgSpeed(units: Units): String = "avg_speed_${speedUnitToken(units)}"
    fun maxSpeed(units: Units): String = "max_speed_${speedUnitToken(units)}"
    fun targetSpeed(units: Units): String = "target_speed_${speedUnitToken(units)}"
    fun actualAvgSpeed(units: Units): String = "actual_avg_speed_${speedUnitToken(units)}"
}
