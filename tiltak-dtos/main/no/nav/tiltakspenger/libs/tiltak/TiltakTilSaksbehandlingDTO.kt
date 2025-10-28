package no.nav.tiltakspenger.libs.tiltak
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.DeltakerStatusDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.GjennomføringDTO
import java.time.LocalDate

data class TiltakTilSaksbehandlingDTO(
    val id: String,
    val gjennomforing: GjennomføringDTO,
    val deltakelseFom: LocalDate?,
    val deltakelseTom: LocalDate?,
    val deltakelseStatus: DeltakerStatusDTO,
    val deltakelsePerUke: Float?,
    val deltakelseProsent: Float?,
    val kilde: String,
)
