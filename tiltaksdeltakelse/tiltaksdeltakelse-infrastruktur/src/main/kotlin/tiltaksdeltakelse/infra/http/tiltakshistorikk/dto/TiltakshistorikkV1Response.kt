package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

/**
 * Respons-kroppen fra `POST /api/v1/historikk` hos `tiltakshistorikk`.
 *
 * Kontrakt: https://github.com/navikt/mulighetsrommet/blob/main/common/tiltakshistorikk-client/src/main/kotlin/no/nav/tiltak/historikk/TiltakshistorikkV1Dto.kt
 */
data class TiltakshistorikkV1Response(
    val historikk: List<TiltakshistorikkV1Dto>,
    /**
     * Meldinger om svaret som helhet — kontrakten bruker dem til å si at svaret er ufullstendig (i dag kun `MANGLER_HISTORIKK_FRA_TEAM_TILTAK`).
     * Deserialiseres som `String` og klassifiseres til `Tiltakshistorikkmelding` i mapperen, slik at en ny meldingstype flyter inn som ukjent i stedet for å velte svaret.
     */
    val meldinger: Set<String>,
)
