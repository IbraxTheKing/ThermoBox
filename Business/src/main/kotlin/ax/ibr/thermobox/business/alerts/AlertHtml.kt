package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.DeliveryKind

/**
 * The HTML version of an alert email, beside its text (multipart/alternative, SmtpClient): the
 * mail apps show it, the text stays for those that do not.
 *
 * One room: what happened in a coloured band, the temperature in large, then a table of the facts.
 * Several rooms: the rule, then one section per kind (in its colour), a row per room: its name
 * and temperature, the threshold, then where and since when in small grey. Styles inline (the
 * mail apps drop <style>), no image, no script. A channel with its own template sends text only.
 */
object AlertHtml {

    fun of(m: AlertMessage, footer: String): String? {
        if (m.templated) return null
        val rooms = m.items.filter { it.event != null }
        val body = when {
            rooms.isEmpty() -> paragraphs(m.text)
            rooms.size == 1 -> one(m, rooms.single())
            else -> many(m)
        }
        val title = m.subject.removePrefix("[ThermoBox] ")
        return """<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>${e(title)}</title></head>""" +
            """<body style="margin:0;padding:0;background:#f3f4f6;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1f2937">""" +
            """<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f3f4f6"><tr><td align="center" style="padding:24px 12px">""" +
            """<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:620px;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb">""" +
            """<tr><td style="background:${color(m.kind)};color:#ffffff;padding:16px 24px;font-size:18px;font-weight:700">${e(title)}</td></tr>""" +
            """<tr><td style="padding:20px 24px">$body</td></tr>""" +
            """<tr><td style="padding:14px 24px;border-top:1px solid #e5e7eb;color:#6b7280;font-size:12px">${e(footer.trim().removePrefix("--").trim())}</td></tr>""" +
            """</table></td></tr></table></body></html>"""
    }

    private fun one(m: AlertMessage, item: AlertItem): String {
        val v = item.values
        val what = when (item.kind) {
            DeliveryKind.RESOLVED -> "est revenue sous le seuil"
            DeliveryKind.REMINDER -> "dépasse toujours le seuil"
            else -> "dépasse le seuil"
        }
        val facts = listOfNotNull(
            v["place"]?.takeIf { it.isNotEmpty() }?.let { "Lieu" to it },
            "Consigne" to v["setpoint"].orEmpty(),
            "Seuil d'alerte" to v["threshold"].orEmpty(),
            "Maximum atteint" to v["peak"].orEmpty(),
            (if (item.kind == DeliveryKind.RESOLVED) "Dépassement" else "Depuis") to
                ((item.event?.exceededSince?.let { AlertMessages.at(it, m.sentAt) } ?: v["since"].orEmpty()) + " (${v["duration"]})"),
            v["devices"]?.takeIf { it.isNotEmpty() }?.let { "Boîtiers" to it },
            "Règle" to v["rule"].orEmpty(),
        )
        return """<p style="margin:0 0 4px;font-size:15px">La température de <b>${e(v["room"].orEmpty())}</b> $what.</p>""" +
            """<p style="margin:0 0 16px;font-size:34px;font-weight:700;color:${color(item.kind)}">${e(v["temperature"].orEmpty())}</p>""" +
            """<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="font-size:14px">""" +
            facts.joinToString("") { (k, value) ->
                """<tr><td width="150" style="padding:6px 12px 6px 0;color:#6b7280;white-space:nowrap;vertical-align:top;border-top:1px solid #f0f1f3">${e(k)}</td>""" +
                    """<td style="padding:6px 0;vertical-align:top;border-top:1px solid #f0f1f3">${e(value)}</td></tr>"""
            } + "</table>"
    }

    private fun many(m: AlertMessage): String {
        val rule = AlertMessages.sharedRule(m.items)
        val out = StringBuilder()
        if (rule != null) out.append("""<p style="margin:0 0 6px;font-size:13px;color:#6b7280">Règle : ${e(rule)}</p>""")
        for ((kind, items) in AlertMessages.sections(m.items)) {
            out.append("""<h2 style="margin:16px 0 6px;font-size:13px;letter-spacing:.04em;text-transform:uppercase;color:${color(kind)}">${e(AlertMessages.sectionTitle(kind))} (${items.size})</h2>""")
            out.append("""<table role="presentation" width="100%" cellpadding="0" cellspacing="0">""")
            items.forEach { item ->
                val line = AlertMessages.roomLine(item, m.sentAt, rule == null)
                out.append("""<tr><td style="padding:10px 0 2px;border-top:1px solid #eef0f2;font-size:15px;font-weight:600">${e(line.room)}</td>""")
                out.append("""<td align="right" style="padding:10px 0 2px;border-top:1px solid #eef0f2;font-size:15px;font-weight:700;color:${color(kind)};white-space:nowrap">${e(line.temperature)}</td></tr>""")
                out.append("""<tr><td colspan="2" style="padding:0 0 10px;font-size:13px;color:#6b7280">${e(line.note)} · ${e(line.detail)}</td></tr>""")
            }
            out.append("</table>")
        }
        if (m.parts > 1) out.append("""<p style="margin:16px 0 0;font-size:12px;color:#6b7280">Partie ${m.part} sur ${m.parts}.</p>""")
        return out.toString()
    }

    private fun paragraphs(text: String) =
        text.split("\n\n").joinToString("") { """<p style="margin:0 0 12px;font-size:15px">${e(it).replace("\n", "<br>")}</p>""" }

    private fun color(k: DeliveryKind) = when (k) {
        DeliveryKind.TRIGGERED -> "#c62828"
        DeliveryKind.REMINDER -> "#e65100"
        DeliveryKind.RESOLVED -> "#2e7d32"
        DeliveryKind.TEST -> "#1565c0"
    }

    private fun e(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
