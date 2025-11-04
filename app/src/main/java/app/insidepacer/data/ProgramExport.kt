package app.insidepacer.data

import android.content.Context
import app.insidepacer.domain.Program
import java.io.File
import java.time.LocalDate

class ProgramExport(private val ctx: Context) {
    private val tplRepo = TemplateRepo(ctx)
    private val progress = ProgramProgressRepo.getInstance(ctx)

    /** Writes program_{id}.csv into app files dir and returns the File. */
    fun exportCsv(program: Program): File {
        val sb = StringBuilder()
        sb.append("date,week,day,template,rest,done\n")
        var idx = 0
        repeat(program.weeks) { w ->
            repeat(program.daysPerWeek) { d ->
                val epoch = program.startEpochDay + idx
                val dayStr = LocalDate.ofEpochDay(epoch).toString()
                val tid = program.templateIdAt(w, d)
                val name = tid?.let { tplRepo.get(it)?.name } ?: ""
                val rest = (tid == null)
                val done = progress.isDone(program.id, epoch)
                sb.append("$dayStr,${w + 1},${d + 1},\"$name\",$rest,$done\n")
                idx++
            }
        }
        val out = File(ctx.filesDir, "program_${program.id}.csv")
        out.writeText(sb.toString())
        return out
    }
}
