package app.insidepacer.csv

import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class CsvWriter private constructor(
    private val writer: BufferedWriter
) : Closeable {

    fun writeRow(values: Iterable<String?>) {
        val row = values.joinToString(separator = ",") { value ->
            val raw = value ?: ""
            val needsQuotes = raw.any { it == '"' || it == ',' || it == '\n' || it == '\r' }
            val escaped = raw.replace("\"", "\"\"")
            if (needsQuotes) "\"$escaped\"" else escaped
        }
        writer.write(row)
        writer.write("\r\n")
    }

    override fun close() {
        writer.flush()
        writer.close()
    }

    companion object {
        private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        fun open(file: File): CsvWriter {
            val stream = file.outputStream()
            return from(stream)
        }

        fun from(stream: OutputStream): CsvWriter {
            stream.write(BOM)
            val writer = OutputStreamWriter(stream, StandardCharsets.UTF_8).buffered()
            return CsvWriter(writer)
        }
    }
}
