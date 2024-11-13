package no.nav.tiltakspenger.libs.datadeling

import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.libs.periodisering.Periode
import java.time.LocalDateTime

data class DatadelingBehandlingDTO(
    val behandlingId: String,
    val sakId: String,
    val periode: Periode,
    val behandlingStatus: Behandlingsstatus,
    val saksbehandler: String?,
    val beslutter: String?,
    val iverksattTidspunkt: LocalDateTime?,
    val tiltak: DatadelingTiltakDTO,

    val fnr: Fnr,
    val saksnummer: String,
    val søknadJournalpostId: String,
    val opprettetTidspunktSaksbehandlingApi: LocalDateTime,

    val mottattTidspunktDatadeling: LocalDateTime,
) {
    // TODO Kew: Denne er kopiert ut fra tiltakspenger-saksbehandling-api så den kan potensielt trekkes ut til libs så den ikke er duplisert
    enum class Behandlingsstatus {
        KLAR_TIL_BEHANDLING,
        UNDER_BEHANDLING,
        KLAR_TIL_BESLUTNING,
        UNDER_BESLUTNING,
        INNVILGET,
    }
}

data class DatadelingTiltakDTO(
    val tiltakNavn: String,
    val eksternTiltakId: String,
    val gjennomføringId: String?,
    val antallDager: Int,
)

internal fun DatadelingTiltakDTO.toDbJson(): String =
    serialize(this)
