package no.nav.tiltakspenger.libs.httpklient

import java.time.LocalDateTime

/**
 * Absolutte veggklokke-tidsstempler (fra klientens [java.time.Clock]) for nøkkelpunkter i livssyklusen til et kall.
 *
 * Utfyller de relative varighetene på [HttpKlientMetadata] ([HttpKlientMetadata.attemptDurations]/[HttpKlientMetadata.totalDuration]) med faktiske [LocalDateTime]-er.
 * Poenget er at konsumenter som må lagre «når skjedde dette» (f.eks. et oversendt-tidspunkt mot et fagsystem) kan lese det rett fra klienten i stedet for å kalle sin egen klokke ved siden av — som både dupliserer klokke-avhengigheten og gir et tidspunkt som er litt ved siden av det klienten faktisk brukte.
 *
 * Tidsstemplene har samme semantikk som `nå(clock)` (`LocalDateTime` i klokkas sone, truncated til mikrosekunder for PostgreSQL-kompatibilitet), slik at en verdi herfra er identisk med det konsumenten ville fått ved å kalle `nå(clock)` selv i samme øyeblikk — og kan lagres direkte i et `LocalDateTime`-felt uten sone-valg eller ny truncation.
 *
 * Alle feltene er nullable fordi ikke alle punkter nås i alle kall:
 * - [authStartet]/[authFullført] er `null` når ingen `AuthTokenProvider` (infrastruktur-modulen) faktisk ble kalt for requesten (per-request `bearerToken`, eksplisitt `Authorization`-header, eller ingen provider konfigurert).
 * - [requestSendt]/[responsMottatt] er `null` når det aldri ble gjort et reelt HTTP-forsøk (pre-flight-feil som bygging/serialisering/auth eller en åpen circuit breaker — jf. [HttpKlientError.RequestIkkeSendt]).
 *
 * @property authStartet Rett før `AuthTokenProvider.hentToken` ble kalt.
 * @property authFullført Rett etter at `AuthTokenProvider.hentToken` returnerte (eller kastet).
 * @property requestSendt Start på det _første_ HTTP-forsøket.
 * Ved retry ligger de påfølgende forsøkene mellom denne og [responsMottatt].
 * @property responsMottatt Slutt på det _siste_ HTTP-forsøket, men bare når det forsøket faktisk ga en respons.
 * `null` når det _endelige_ utfallet ikke ga en respons (timeout/nettverksfeil på siste forsøk), også om et tidligere forsøk fikk en respons.
 */
data class HttpKlientTidsstempler(
    val authStartet: LocalDateTime?,
    val authFullført: LocalDateTime?,
    val requestSendt: LocalDateTime?,
    val responsMottatt: LocalDateTime?,
) {
    companion object {
        /** Ingen tidsstempler registrert — korrekt verdi for feil som oppstår før noe som helst ble tidfestet. */
        val INGEN = HttpKlientTidsstempler(
            authStartet = null,
            authFullført = null,
            requestSendt = null,
            responsMottatt = null,
        )
    }
}
