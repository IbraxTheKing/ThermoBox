package ax.ibr.thermobox.restserver.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuditTargetsTest {

    private data class Created(val id: Long, val name: String)

    @Test
    fun `the values sent go to the log without their secrets`() {
        val body = """{"name":"Ops","email":{"host":"smtp","password":"s3cret","recipients":["a@b.fr"]},
            "webhook":{"url":"https://discord.com/api/webhooks/1/abc","format":"DISCORD"},"signingSecret":null}"""
        val s = AuditTargets.sanitized(body.toByteArray())!!
        assertEquals("•••", s["email"]["password"].asText())
        assertEquals("•••", s["webhook"]["url"].asText())
        assertEquals("smtp", s["email"]["host"].asText())
        assertEquals("DISCORD", s["webhook"]["format"].asText())
        assertFalse(s.has("signingSecret"))
        assertFalse("s3cret" in s.toString() || "abc" in s.toString())
        assertNull(AuditTargets.sanitized("not json".toByteArray()))
    }

    @Test
    fun `a creation gives its id and its name in its answer`() {
        assertEquals(4L to "Salle 101", AuditTargets.fromAnswer(Created(4, "Salle 101")))
        assertEquals(null to null, AuditTargets.fromAnswer(null))
        assertEquals(null to null, AuditTargets.fromAnswer(byteArrayOf(1, 2)))
    }

    @Test
    fun `details hold the changes, what they replaced and where`() {
        val changes = AuditTargets.sanitized("""{"value":22.5}""".toByteArray())
        assertEquals("""{"changes":{"value":22.5},"before":{"setpoint":21.0},"place":"Bâtiment A"}""", AuditTargets.details(changes, mapOf("setpoint" to 21.0), "Bâtiment A"))
        assertNull(AuditTargets.details(null, emptyMap(), null))
    }
}
