package no.nav.tiltakspenger.libs.tiltak
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.DeltakerStatusDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakType
import java.time.LocalDate

data class TiltakTilSaksbehandlingDTO(
    val id: String,
    val deltakelseFom: LocalDate?,
    val deltakelseTom: LocalDate?,
    val typeNavn: String,
    val typeKode: TiltakType,
    val deltakelseStatus: DeltakerStatusDTO,
    val deltakelsePerUke: Float?,
    val deltakelseProsent: Float?,
    val kilde: String,
)
