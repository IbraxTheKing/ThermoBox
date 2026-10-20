package ax.ibr.thermobox.jsf.util

/**
 * Release notes (GitHub Markdown) to HTML, for h:outputText escape="false". The text is
 * HTML-escaped first and only a few constructs are turned back into tags (headings, lists,
 * bold, code, links to http(s) URLs), so nothing from the notes can inject markup or script.
 */
object Markdown {
    private val TITRE = Regex("""^(#{1,6})\s+(.*)$""")
    private val PUCE = Regex("""^\s*[-*+]\s+(.*)$""")
    private val GRAS = Regex("""\*\*(.+?)\*\*""")
    private val CODE = Regex("""`([^`]+)`""")
    private val LIEN = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)""")
    private val URL = Regex("""(?<!href=")(?<!">)\bhttps?://[^\s<]+""")

    fun versHtml(texte: String?): String {
        if (texte.isNullOrBlank()) return "<p class=\"tb-muted\">Pas de notes de version.</p>"
        val html = StringBuilder()
        var liste = false
        val paragraphe = ArrayList<String>()
        fun fermerParagraphe() {
            if (paragraphe.isNotEmpty()) html.append("<p>").append(paragraphe.joinToString("<br/>")).append("</p>")
            paragraphe.clear()
        }
        fun fermerListe() {
            if (liste) html.append("</ul>")
            liste = false
        }
        for (brute in texte.replace("\r", "").lines()) {
            val ligne = enLigne(echapper(brute.trimEnd()))
            val titre = TITRE.matchEntire(brute.trim())
            val puce = PUCE.matchEntire(brute)
            when {
                brute.isBlank() -> { fermerParagraphe(); fermerListe() }
                titre != null -> {
                    fermerParagraphe(); fermerListe()
                    // h3 at most: the page already has its h1 / h2.
                    val n = (titre.groupValues[1].length + 2).coerceAtMost(6)
                    html.append("<h$n>").append(enLigne(echapper(titre.groupValues[2]))).append("</h$n>")
                }
                puce != null -> {
                    fermerParagraphe()
                    if (!liste) html.append("<ul>")
                    liste = true
                    html.append("<li>").append(enLigne(echapper(puce.groupValues[1]))).append("</li>")
                }
                else -> { fermerListe(); paragraphe += ligne }
            }
        }
        fermerParagraphe()
        fermerListe()
        return html.toString()
    }

    private fun echapper(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    /** Inline constructs, on already escaped text. */
    private fun enLigne(s: String): String {
        var r = CODE.replace(s) { "<code>${it.groupValues[1]}</code>" }
        r = GRAS.replace(r) { "<strong>${it.groupValues[1]}</strong>" }
        r = LIEN.replace(r) { lien(it.groupValues[2], it.groupValues[1]) }
        r = URL.replace(r) { lien(it.value, raccourcir(it.value)) }
        return r
    }

    private fun lien(url: String, texte: String) = "<a href=\"$url\" target=\"_blank\" rel=\"noopener noreferrer\">$texte</a>"

    /** « …/pull/38 » rather than the whole URL, as GitHub shows it. */
    private fun raccourcir(url: String): String {
        val m = Regex("""https://github\.com/[^/]+/[^/]+/(pull|issues)/(\d+)""").matchEntire(url)
        return if (m != null) "#${m.groupValues[2]}" else url
    }
}
