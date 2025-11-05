package app.insidepacer.data

import android.content.Context
import app.insidepacer.core.formatDuration
import app.insidepacer.csv.CsvFields
import app.insidepacer.csv.CsvWriter
import app.insidepacer.data.Units
import app.insidepacer.domain.Program
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ProgramExport(private val ctx: Context) {
    private val tplRepo = TemplateRepo(ctx)
    private val progress = ProgramProgressRepo.getInstance(ctx)

    /** Writes program_{id}.csv into app files dir and returns the File. */
    fun exportCsv(program: Program): File {
        val units = runBlocking { SettingsRepo(ctx).units.first() }
        return exportCsv(program, units)
    }

    fun exportCsv(program: Program, units: Units): File {
        val out = File(ctx.filesDir, "program_${program.id}.csv")
        CsvWriter.open(out).use { writer ->
            writer.writeRow(
                listOf(
                    CsvFields.PROGRAM_DATE,
                    CsvFields.PROGRAM_WEEK,
                    CsvFields.PROGRAM_DAY,
                    CsvFields.PROGRAM_TEMPLATE,
                    CsvFields.PROGRAM_REST,
                    CsvFields.PROGRAM_DONE,
                    CsvFields.PROGRAM_NAME,
                    CsvFields.UNITS,
                    CsvFields.TOTAL_DURATION_HMS
                )
            )
            var idx = 0
            repeat(program.weeks) { w ->
                repeat(program.daysPerWeek) { d ->
                    val epoch = program.startEpochDay + idx
                    val dayStr = LocalDate.ofEpochDay(epoch).toString()
                    val tid = program.templateIdAt(w, d)
                    val template = tid?.let { tplRepo.get(it) }
                    val name = template?.name ?: ""
                    val rest = (tid == null)
                    val done = progress.isDone(program.id, epoch)
                    val totalDuration = template?.segments?.sumOf { it.seconds } ?: 0
                    writer.writeRow(
                        listOf(
                            dayStr,
                            (w + 1).toString(),
                            (d + 1).toString(),
                            name,
                            rest.toString(),
                            done.toString(),
                            program.name,
                            units.name,
                            formatDuration(totalDuration)
                        )
                    )
                    idx++
                }
            }
        }
        return out
    }
}
