package no.nav.tiltakspenger.libs.meldekort

import no.nav.tiltakspenger.libs.periodisering.PeriodeDTO
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Representerer et meldekort som bruker har fylt ut.
 * Dette lagres først i meldekort-api og sendes til saksbehandling-api for videre behandling.
 * @param id Unik id (ULID/UUID) for dette meldekortet
 * @param meldeperiodeId En unik versjon av meldeperioden. Alternativ til å sende meldeperiodeKjedeId+versjon.
 */
@Suppress("unused")
data class BrukerutfyltMeldekortDTO(
    val id: String,
    val meldeperiodeId: String,
    val sakId: String,
    val periode: PeriodeDTO,
    val mottatt: LocalDateTime,
    val dager: Map<LocalDate, Status>,
    val journalpostId: String,
) {
    enum class Status {
        DELTATT_UTEN_LØNN_I_TILTAKET,
        DELTATT_MED_LØNN_I_TILTAKET,
        FRAVÆR_SYK,
        FRAVÆR_SYKT_BARN,
        FRAVÆR_GODKJENT_AV_NAV,
        FRAVÆR_ANNET,
        IKKE_BESVART,
    }
}
