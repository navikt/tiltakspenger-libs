package no.nav.tiltakspenger.libs.logging

import no.nav.tiltakspenger.libs.logging.infra.KotlinLoggingSikkerlogg

/**
 * Sikkerlogg for logglinjer som kan inneholde personopplysninger og derfor kun skal til team-logs.
 * Se https://docs.nais.io/observability/logging/how-to/team-logs/ for konfigurasjon.
 * Implementasjoner navngis etter teknologien de bruker, som [KotlinLoggingSikkerlogg].
 * Companion-objektet er default-instansen og gir kildekompatibilitet for statiske kallsteder inntil konsumentene injiserer interfacet.
 */
interface Sikkerlogg {
    fun trace(throwable: Throwable? = null, loggstatement: () -> Any?)

    fun debug(throwable: Throwable? = null, loggstatement: () -> Any?)

    fun info(throwable: Throwable? = null, loggstatement: () -> Any?)

    fun warn(throwable: Throwable? = null, loggstatement: () -> Any?)

    fun error(throwable: Throwable? = null, loggstatement: () -> Any?)

    // Delegeringen til en infra-plassert implementasjon er en bevisst, midlertidig kant som holder statiske kallsteder kompilerende.
    // Den fjernes når konsumentene er over på injisert instans.
    companion object : Sikkerlogg by KotlinLoggingSikkerlogg
}
