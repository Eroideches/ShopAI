package com.eroideches.shopai.core.extract

import com.eroideches.shopai.core.rules.SiteRule

/** Riconosce CAPTCHA, controlli anti-bot e pagine di consenso che bloccano la lettura. */
object BlockDetector {
    private val generic = listOf(
        "captcha", "are you a robot", "sei un robot", "non sono un robot", "robot check", "verify you are human",
        "verifica di essere un essere umano", "validatecaptcha", "inserisci i caratteri", "challenge-platform",
        "px-captcha", "slide to verify", "scorri per verificare", "punish", "access denied", "accesso negato",
        "unusual traffic", "traffico insolito", "security check", "controllo di sicurezza", "bot detection",
    )

    data class Verdict(val blocked: Boolean, val marker: String? = null)

    fun check(html: String, rule: SiteRule): Verdict {
        val sample = (if (html.length > 400_000) html.take(200_000) + html.takeLast(100_000) else html).lowercase()
        for (m in rule.blockMarkers + generic) {
            if (sample.contains(m.lowercase())) {
                // Pagine normali possono citare "captcha" in uno script: conta solo se la pagina è piccola o ha pochi link.
                val links = Regex("<a\\s").findAll(sample).count()
                if (links < 40 || m in rule.blockMarkers) return Verdict(true, m)
            }
        }
        return Verdict(false)
    }
}
