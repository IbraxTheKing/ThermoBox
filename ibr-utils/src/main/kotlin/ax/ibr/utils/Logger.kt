package ax.ibr.utils

class Logger(
    private val technology: LogTechnology = LogTechnology.JSON
) {

    constructor(msg: String) : this() {
        print(msg, LogLevel.INFO)
        log(msg, LogLevel.INFO)
    }

    constructor(msg: String, showInConsole: Boolean) : this() {
        if (showInConsole) {
            print(msg, LogLevel.INFO)
        }
        log(msg, LogLevel.DEBUG)
    }

    constructor(msg: String, type: LogLevel) : this() {
        print(msg, type)
        log(msg, type)
    }


    private fun log(msg: String, type: LogLevel) {

        val entry = LogEntry(
            message = msg,
            level = type,
            timestamp = System.currentTimeMillis()
        )

        when (technology) {
            LogTechnology.JSON -> JsonLogger.write(entry)
            LogTechnology.DB -> {

            }
        }
    }


    private fun print(msg: String, logLevel: LogLevel) {
        println("$logLevel : $msg")
    }
}


data class LogEntry(
    val message: String,
    val level: LogLevel,
    val timestamp: Long
)


enum class LogLevel {
    ERROR, WARN, INFO, DEBUG, TRACE
}


enum class LogTechnology {
    JSON, DB
}