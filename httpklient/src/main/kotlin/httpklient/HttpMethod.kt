package no.nav.tiltakspenger.libs.httpklient

/**
 * HTTP-metodene tiltakspenger-appene faktisk bruker; `DELETE`/`HEAD`/`OPTIONS` er bevisst utelatt (null bruk i behovsinventaret).
 * Public kun fordi [no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerDecisionContext] eksponerer den.
 */
enum class HttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
}
