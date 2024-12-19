package no.nav.tiltakspenger.libs.datadeling

import no.nav.tiltakspenger.libs.json.serialize
import java.time.LocalDate
import java.time.LocalDateTime

data class DatadelingBehandlingDTO(
    val behandlingId: String,
    val sakId: String,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val behandlingStatus: Behandlingsstatus,
    val saksbehandler: String?,
    val beslutter: String?,
    val iverksattTidspunkt: LocalDateTime?,
    val tiltak: DatadelingTiltakDTO,

    val fnr: String,
    val saksnummer: String,
    val søknadJournalpostId: String,
    val opprettetTidspunktSaksbehandlingApi: LocalDateTime,
) {
    enum class Behandlingsstatus {
        KLAR_TIL_BEHANDLING,
        UNDER_BEHANDLING,
        KLAR_TIL_BESLUTNING,
        UNDER_BESLUTNING,
        VEDTATT,
    }
}

data class DatadelingTiltakDTO(
    val tiltakNavn: String,
    val eksternTiltakdeltakerId: String,
    val gjennomføringId: String?,
)

internal fun DatadelingTiltakDTO.toDbJson(): String =
    serialize(this)
