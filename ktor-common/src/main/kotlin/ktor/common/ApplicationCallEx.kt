package no.nav.tiltakspenger.libs.ktor.common

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/** Kun ment brukt av ApplicationCall extension functions */
val DEFAULT_APPLICATION_CALL_EX_LOGGER: KLogger by lazy { KotlinLogging.logger {} }
