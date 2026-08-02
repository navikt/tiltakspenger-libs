package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Beskjed fra `tiltakshistorikk` om svaret som helhet, ikke om en enkelt deltakelse.
 *
 * Kontrakt: https://github.com/navikt/mulighetsrommet/blob/main/common/tiltakshistorikk-client/src/main/kotlin/no/nav/tiltak/historikk/TiltakshistorikkV1Dto.kt
 *
 * Kontrakten bruker meldingene til å si at et svar er ufullstendig: en kilde svarte ikke, og deltakelser derfra kan mangle.
 * Uten dem ser et delvis svar komplett ut — bruker finner ikke tiltaket sitt i søknaden, og saksbehandler ser en historikk som ser hel ut.
 * Kompletthet hører derfor til hentingen og samletypen, aldri til den enkelte deltakelsen.
 *
 * En melding vi ikke kjenner igjen flyter inn som [Ukjent] i stedet for å velte deserialiseringen, samme regel som for ukjente kildeverdier ellers i modulen.
 */
sealed interface Tiltakshistorikkmelding {
    /**
     * Koden slik kontrakten skriver den.
     * Kun til visning og gjenkjenning — diskriminering skjer på typen.
     */
    val kode: String

    /**
     * Kilden svaret er ufullstendig for, eller `null` når meldingen ikke peker på en bestemt kilde.
     */
    val manglendeKilde: Tiltakskilde?

    /** Team Tiltak svarte ikke, og avtaler derfra kan mangle i svaret. */
    data object ManglerHistorikkFraTeamTiltak : Tiltakshistorikkmelding {
        override val kode = "MANGLER_HISTORIKK_FRA_TEAM_TILTAK"
        override val manglendeKilde = Tiltakskilde.TeamTiltak
    }

    /**
     * En melding vi ikke kjenner igjen.
     * Kontrakten kan få nye verdier, og en ny melding betyr sannsynligvis at et svar er ufullstendig på en ny måte — den skal varsles på, ikke forsvinne stille.
     */
    data class Ukjent(
        override val kode: String,
    ) : Tiltakshistorikkmelding {
        override val manglendeKilde: Tiltakskilde? = null
    }
}
