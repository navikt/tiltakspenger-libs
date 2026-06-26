package no.nav.tiltakspenger.libs.httpklient

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.retry.RetryConfig
import java.net.URI
import kotlin.time.Duration

/**
 * En ferdig materialisert request, slik den ser ut etter at [RequestBuilder] er kjørt og klientens default-headere er lagt til.
 *
 * Dette er typen [HttpKlient.request] mottar.
 * De reified verb-extensionene bygger en [RequestBuilder], materialiserer den til denne typen, og kaller [HttpKlient.request] med den.
 * Implementasjoner (den virkelige klienten og `HttpKlientFake`) får dermed rene data å jobbe med i stedet for en builder-callback, og fakes kan asserte direkte på requesten uten å røre [RequestBuilder].
 *
 * Headere bevarer innsettingsrekkefølge, med eventuelle default-headere som klienten legger til (JSON `Content-Type` for body, `Accept` for response-type), til slutt.
 * En eksplisitt `Authorization`-header satt via [RequestBuilder.header] er inkludert her, og beholdes uendret av implementasjonen.
 * Det som _ikke_ ligger her, er en bearer-token fra [authToken] eller klientens `authTokenProvider`; den resolves og materialiseres til `Authorization: Bearer ...` av implementasjonen ved sending.
 *
 * Per-request-overstyringer ([retryConfig], [circuitBreakerConfig], [loggingConfig], [successStatus]) følger med fordi implementasjonen trenger dem.
 * De er funksjons-/konfig-verdier; `equals`/`hashCode` faller derfor tilbake på referanselikhet for disse feltene, så tester bør asserte på [uri], [method], [headers] og [body].
 */
data class HttpKlientRequest(
    val uri: URI,
    val method: HttpMethod,
    val headers: Map<String, List<String>>,
    val body: Body?,
    val timeout: Duration?,
    val authToken: AccessToken?,
    val successStatus: ((Int) -> Boolean)?,
    val loggingConfig: HttpKlientLoggingConfig?,
    val retryConfig: RetryConfig?,
    val circuitBreakerConfig: CircuitBreakerConfig?,
) {
    sealed interface Body {
        /**
         * Rå tekst-body.
         * `httpklient` legger ikke til JSON-headere for denne varianten.
         */
        data class Raw(val body: String) : Body

        /**
         * Ferdigserialisert JSON-string.
         * `httpklient` legger til standard JSON-headere hvis de mangler.
         */
        data class RawJson(val body: String) : Body

        /**
         * DTO/verdi som serialiseres med `tiltakspenger-libs/json` før requesten sendes.
         */
        data class Json(val value: Any) : Body
    }
}
