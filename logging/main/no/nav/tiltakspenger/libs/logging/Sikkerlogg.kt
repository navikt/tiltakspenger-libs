package no.nav.tiltakspenger.libs.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

val sikkerlogg: KLogger by lazy { KotlinLogging.logger("tjenestekall") }
