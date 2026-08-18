package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

/**
 * Request-kroppen til `POST /api/v1/historikk` hos `tiltakshistorikk`.
 *
 * Kontrakt: https://github.com/navikt/mulighetsrommet/blob/main/common/tiltakshistorikk-client/src/main/kotlin/no/nav/tiltak/historikk/TiltakshistorikkV1Dto.kt
 */
data class TiltakshistorikkV1Request(
    /**
     * Alle identene deltakelsene skal slås opp for — kontrakten krever at konsumenten selv sender nåværende og historiske fødselsnummer.
     */
    val identer: List<NorskIdentDto>,
)
