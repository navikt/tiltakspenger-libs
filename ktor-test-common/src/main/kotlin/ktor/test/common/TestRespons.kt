package no.nav.tiltakspenger.libs.ktor.test.common

import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod

/**
 * Responsen fra [defaultRequest], ferdig lest.
 *
 * Poenget med typen er at ktor ikke lekker ut til kallstedene: `testApplication` sin klient er eneste vei inn til test-serveren, men den skal bo inne i [defaultRequest], ikke i hver eneste rutetest.
 * Vokabularet er derfor det samme som `httpklient` bruker — [HttpMethod], statuskode som `Int` og Content-Type som `String` — slik at en rutetest og en klienttest snakker likt om HTTP.
 * Alt er lest på forhånd, så aksessorene er vanlige verdier og ikke `suspend`-kall; kallstedene slipper `bodyAsText()`-dansen i `apply`-blokker.
 *
 * [method] og [sti] er requestens egne verdier, ikke responsens.
 * De er med fordi kontraktsverifisering mot openapi trenger sti og metode sammen med responsen, og alternativet ville vært å hente dem fra ktor sin `HttpResponse.request` på kallstedet.
 * [sti] er den kodede stien uten skjema, host og query — altså `/vedtak/perioder`, uansett om kallstedet ba om en full URL eller bare stien.
 *
 * [body] er bodyen dekodet som tekst etter responsens charset, [bytes] er de samme dataene rå.
 * Binære responser (PDF-er) skal leses via [bytes] — [body] er da en meningsløs tekstdekoding.
 *
 * Ikke en data class: `ByteArray` har referanselikhet i `equals`, så generert verdilikhet ville løyet om [bytes].
 */
class TestRespons(
    val method: HttpMethod,
    val sti: String,
    val statusCode: Int,
    /** Responsens Content-Type slik ktor gjengir den, f.eks. `application/json` eller `text/plain; charset=UTF-8`; `null` når responsen ikke har noen. */
    val contentType: String?,
    val body: String,
    val bytes: ByteArray,
)
