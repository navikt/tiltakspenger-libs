package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

/**
 * Respons-kroppen fra `POST /api/v1/historikk` hos `tiltakshistorikk`.
 *
 * Kontrakt: https://github.com/navikt/mulighetsrommet/blob/main/common/tiltakshistorikk-client/src/main/kotlin/no/nav/tiltak/historikk/TiltakshistorikkV1Dto.kt
 */
data class TiltakshistorikkV1Response(
    val historikk: List<TiltakshistorikkV1Dto>,
)
