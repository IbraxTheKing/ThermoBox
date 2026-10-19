package ax.ibr.thermobox.restserver.dto

data class ErrorResponse(
    val status: Int,
    val message: String,
    val timestamp: String = java.time.Instant.now().toString()
)