package ax.ibr.thermobox.jsf.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownTest {

    @Test
    fun `notes de version GitHub`() {
        val html = Markdown.versHtml(
            "## What's Changed\n* Add **rate limiting** in `SecurityFilter` by @ib in https://github.com/IbraxTheKing/ThermoBox/pull/38\n\n" +
                "**Full Changelog**: https://github.com/IbraxTheKing/ThermoBox/compare/26.10.1...26.10.2"
        )
        assertEquals(
            "<h4>What&#39;s Changed</h4><ul><li>Add <strong>rate limiting</strong> in <code>SecurityFilter</code> by @ib in " +
                "<a href=\"https://github.com/IbraxTheKing/ThermoBox/pull/38\" target=\"_blank\" rel=\"noopener noreferrer\">#38</a></li></ul>" +
                "<p><strong>Full Changelog</strong>: <a href=\"https://github.com/IbraxTheKing/ThermoBox/compare/26.10.1...26.10.2\" " +
                "target=\"_blank\" rel=\"noopener noreferrer\">https://github.com/IbraxTheKing/ThermoBox/compare/26.10.1...26.10.2</a></p>",
            html,
        )
    }

    @Test
    fun `aucun balisage venant des notes`() {
        val html = Markdown.versHtml("<script>alert(1)</script>\n[clic](javascript:alert(1)) [ok](https://x.org/a?b=1&c=2)\n<img src=x onerror=alert(1)>")
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("<img"))
        assertFalse(html.contains("href=\"javascript"))
        assertTrue(html.contains("href=\"https://x.org/a?b=1&amp;c=2\""), html)
    }
}
