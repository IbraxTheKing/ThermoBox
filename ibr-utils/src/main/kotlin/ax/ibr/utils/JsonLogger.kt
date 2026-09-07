package ax.ibr.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File


object JsonLogger {

    private val file = File("env/axibr/logs.json")


    fun write(entry: LogEntry) {

        val logs = readLogs().toMutableList()

        logs.add(entry)

        file.writeText(
            Json.encodeToString(logs)
        )
    }


    private fun readLogs(): List<LogEntry> {

        if (!file.exists()) {
            file.createNewFile()
            file.writeText("[]")
        }

        val content = file.readText()

        return Json.decodeFromString(content)
    }
}