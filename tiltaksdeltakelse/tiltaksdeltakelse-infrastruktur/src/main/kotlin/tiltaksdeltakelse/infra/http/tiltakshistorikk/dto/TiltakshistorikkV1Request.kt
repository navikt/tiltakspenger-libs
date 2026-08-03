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
    /**
     * Maks alder på deltakelsene i svaret, eller `null` for hele historikken.
     * Brukes bevisst ikke — klienten sender alltid `null`: kuttet måles hos kilden som `age(coalesce(slutt_dato, registrert_dato))` for Arena/Komet og `sluttDato ?: startDato ?: opprettet` for Team Tiltak, så en pågående deltakelse uten sluttdato kuttes også hvis den er registrert eller startet for mer enn N år siden.
     * Tidsromsfiltrering er konsumentenes presentasjonsregel og gjøres internt hos oss, på data vi faktisk har sett.
     * Nøkkelen må likevel alltid være med i JSON-en (kontraktens kotlinx-modell har ingen default), så feltet har bevisst ingen default her heller.
     */
    val maxAgeYears: Int?,
)
