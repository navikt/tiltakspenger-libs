package no.nav.tiltakspenger.libs.logging

import mu.KLogger
import mu.KotlinLogging

val sikkerlogg: KLogger by lazy { KotlinLogging.logger("tjenestekall") }
