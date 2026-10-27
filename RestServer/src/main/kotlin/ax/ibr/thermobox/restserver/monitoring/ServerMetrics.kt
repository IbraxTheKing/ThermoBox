package ax.ibr.thermobox.restserver.monitoring

import java.lang.management.ManagementFactory

/**
 * What the server process uses right now (GET /api/v2/server/metrics, ADMIN): processor, memory,
 * threads. Read by the load test (loadtest/thermobox.js) every few seconds, to put the load of the
 * server next to its response times. Inside a container the figures are the container's (CPU
 * share, memory limit), as the JVM sees them.
 */
data class ServerMetricsDto(
    /** CPU used by this process, in % of the processors it may use (0 to 100); null if unknown. */
    val processCpu: Double?,
    /** CPU used by the whole machine (or container), same unit. */
    val systemCpu: Double?,
    val processors: Int,
    /** Java heap: used, committed (reserved from the system), max (-Xmx), in MB. */
    val heapUsedMb: Long,
    val heapCommittedMb: Long,
    val heapMaxMb: Long,
    /** Memory outside the heap (classes, compiled code…), in MB. */
    val nonHeapUsedMb: Long,
    val threads: Int,
    /** Garbage collections since the start, and the time they took. */
    val gcCount: Long,
    val gcTimeMs: Long,
    val uptimeSeconds: Long,
) {
    companion object {
        private const val MB = 1024L * 1024

        fun now(): ServerMetricsDto {
            val os = ManagementFactory.getOperatingSystemMXBean()
            val sun = os as? com.sun.management.OperatingSystemMXBean
            val memory = ManagementFactory.getMemoryMXBean()
            val heap = memory.heapMemoryUsage
            val gcs = ManagementFactory.getGarbageCollectorMXBeans()
            fun percent(v: Double?) = v?.takeIf { it >= 0 }?.let { Math.round(it * 1000) / 10.0 }
            return ServerMetricsDto(
                processCpu = percent(sun?.processCpuLoad),
                systemCpu = percent(sun?.cpuLoad),
                processors = os.availableProcessors,
                heapUsedMb = heap.used / MB,
                heapCommittedMb = heap.committed / MB,
                heapMaxMb = heap.max.takeIf { it >= 0 }?.div(MB) ?: -1,
                nonHeapUsedMb = memory.nonHeapMemoryUsage.used / MB,
                threads = ManagementFactory.getThreadMXBean().threadCount,
                gcCount = gcs.sumOf { it.collectionCount.coerceAtLeast(0) },
                gcTimeMs = gcs.sumOf { it.collectionTime.coerceAtLeast(0) },
                uptimeSeconds = ManagementFactory.getRuntimeMXBean().uptime / 1000,
            )
        }
    }
}
