package no.nav.tiltakspenger.libs.httpklient

import io.github.oshai.kotlinlogging.KLogger

/**
 * Loggnivå for en enkelt logg-kategori i [HttpKlientLoggingConfig].
 * [OFF] slår kategorien helt av uten å påvirke de andre kategoriene, slik at konsumenter kan skru ned støy (f.eks. suksess-logging) uten å miste feillogging.
 * De øvrige verdiene svarer til nivåene på `KLogger`/`Sikkerlogg`; `Sikkerlogg` har ikke `trace`, så [TRACE] mappes til `debug` der.
 */
enum class HttpKlientLogNivå {
    OFF,
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Granulær styring av `httpklient`-logging.
 *
 * I tillegg til [logger], [loggTilSikkerlogg] og [inkluderHeadere] styres loggnivået per kategori av kall:
 * - [suksessNivå] — kall som ble godtatt av `successStatus`-predikatet (default [HttpKlientLogNivå.INFO]).
 * - [klientfeilNivå] — respons med `4xx`-status som ikke ble godtatt som suksess (default [HttpKlientLogNivå.ERROR]).
 * - [serverfeilNivå] — respons med annen uventet status (typisk `5xx`, men også f.eks. `3xx` når den ikke godtas) (default [HttpKlientLogNivå.ERROR]).
 * - [feilNivå] — feil der vi aldri fikk en godtatt respons: transport-/timeout-/serialiserings-/deserialiserings-/auth-/circuit breaker-feil (default [HttpKlientLogNivå.ERROR]).
 * - [skipCacheRetryNivå] — diagnostikk når en skip-cache-retry ikke hjalp: et ferskt token ble også avvist (typisk persistent `401`/`403`) (default [HttpKlientLogNivå.WARN]).
 * - [excessiveRetriesNivå] — varsel om overdreven retry-bruk når en request passerer [no.nav.tiltakspenger.libs.httpklient.retry.RetryConfig.excessiveRetriesThreshold] og ingen egen `onExcessiveRetries`-hook er satt (default [HttpKlientLogNivå.WARN]).
 *
 * Sett en kategori til [HttpKlientLogNivå.OFF] for å skru den av, eller hev/senk nivået etter behov.
 * Nivået gjelder både [logger] og — når [loggTilSikkerlogg] er `true` — `Sikkerlogg`.
 */
data class HttpKlientLoggingConfig(
    val logger: KLogger? = null,
    val loggTilSikkerlogg: Boolean = false,
    val inkluderHeadere: Boolean = false,
    val suksessNivå: HttpKlientLogNivå = HttpKlientLogNivå.INFO,
    val klientfeilNivå: HttpKlientLogNivå = HttpKlientLogNivå.ERROR,
    val serverfeilNivå: HttpKlientLogNivå = HttpKlientLogNivå.ERROR,
    val feilNivå: HttpKlientLogNivå = HttpKlientLogNivå.ERROR,
    val skipCacheRetryNivå: HttpKlientLogNivå = HttpKlientLogNivå.WARN,
    val excessiveRetriesNivå: HttpKlientLogNivå = HttpKlientLogNivå.WARN,
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
    var suksessNivå: HttpKlientLogNivå = HttpKlientLogNivå.INFO
    var klientfeilNivå: HttpKlientLogNivå = HttpKlientLogNivå.ERROR
    var serverfeilNivå: HttpKlientLogNivå = HttpKlientLogNivå.ERROR
    var feilNivå: HttpKlientLogNivå = HttpKlientLogNivå.ERROR
    var skipCacheRetryNivå: HttpKlientLogNivå = HttpKlientLogNivå.WARN
    var excessiveRetriesNivå: HttpKlientLogNivå = HttpKlientLogNivå.WARN

    fun build(): HttpKlientLoggingConfig {
        return HttpKlientLoggingConfig(
            logger = logger,
            loggTilSikkerlogg = loggTilSikkerlogg,
            inkluderHeadere = inkluderHeadere,
            suksessNivå = suksessNivå,
            klientfeilNivå = klientfeilNivå,
            serverfeilNivå = serverfeilNivå,
            feilNivå = feilNivå,
            skipCacheRetryNivå = skipCacheRetryNivå,
            excessiveRetriesNivå = excessiveRetriesNivå,
        )
    }
}
