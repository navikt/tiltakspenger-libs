package no.nav.tiltakspenger.libs.httpklient

/**
 * Hvilken fase av kallet som time-et ut.
 *
 * Skillet er verdt å bære fordi de to tilfellene peker på helt ulike årsaker, og fordi stacktracen aldri sier hvilket det var: en `java.net.http.HttpTimeoutException` lages på klientens egen I/O-tråd, uten en eneste applikasjonsframe.
 */
enum class Timeoutfase {
    /**
     * Oppkoblingen (TCP/TLS) ble ikke ferdig innen transportens `connectTimeout`.
     * Serveren har da garantert ikke sett requesten — i motsetning til det [HttpKlientError.IngenRespons] ellers garanterer — så et nytt forsøk er trygt også for ikke-idempotente kall.
     * Peker typisk på nettverk, DNS eller en mottaker som ikke tar imot forbindelser, ikke på at mottakeren er treg.
     */
    Oppkobling,

    /**
     * Forbindelsen var på plass, men svaret kom ikke innen `HttpKlientConfig.timeout`.
     * Det er ukjent om serveren rakk å motta og behandle requesten.
     * Peker typisk på en treg mottaker.
     */
    Svar,
}
