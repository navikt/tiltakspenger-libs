package no.nav.tiltakspenger.libs.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

@Deprecated("Slutter å virke 1/6 2025 og bør erstattes med ny Sikkerlogg")
val sikkerlogg: KLogger by lazy { KotlinLogging.logger("tjenestekall") }
