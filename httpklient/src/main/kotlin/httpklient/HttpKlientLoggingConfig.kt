package no.nav.tiltakspenger.libs.httpklient

import io.github.oshai.kotlinlogging.KLogger

data class HttpKlientLoggingConfig(
    val logger: KLogger? = null,
    val loggTilSikkerlogg: Boolean = false,
    val inkluderHeadere: Boolean = false,
) {
    companion object {
        val Disabled = HttpKlientLoggingConfig()

        fun build(build: HttpKlientLoggingConfigBuilder.() -> Unit): HttpKlientLoggingConfig {
            return HttpKlientLoggingConfigBuilder().apply(build).build()
        }
    }
}

class HttpKlientLoggingConfigBuilder {
    var logger: KLogger? = null
    var loggTilSikkerlogg: Boolean = false
    var inkluderHeadere: Boolean = false

    fun build(): HttpKlientLoggingConfig {
        return HttpKlientLoggingConfig(
            logger = logger,
            loggTilSikkerlogg = loggTilSikkerlogg,
            inkluderHeadere = inkluderHeadere,
        )
    }
}
