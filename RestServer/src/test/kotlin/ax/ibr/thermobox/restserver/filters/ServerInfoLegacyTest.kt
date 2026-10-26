package ax.ibr.thermobox.restserver.filters

import ax.ibr.thermobox.restserver.resources.ServerInfoDto
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** GET /api/server, the 26.11 endpoint, keeps answering from 26.12 on. */
class ServerInfoLegacyTest {

    @Test
    fun `api server is served by api v2 server`() {
        assertEquals("api/v2/server", LegacyApi.englishPath("api/server"))
        assertEquals("api/v2/server/logo", LegacyApi.englishPath("/api/server/logo"))
    }

    @Test
    fun `the old API gets the same keys and values`() {
        val json = ObjectMapper()
        val v2 = json.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
            ServerInfoDto("ThermoBox", "26.12.0", ServerInfoDto.API_VERSIONS, "/api/v2/server/logo?v=1", "default"),
        )
        val v1 = LegacyApi.toLegacy(v2)
        assertEquals(v2, v1)
        assertEquals("26.12.0", v1["version"].asText())
        assertEquals(listOf("v1", "v2"), v1["apiVersions"].map { it.asText() })
    }
}
