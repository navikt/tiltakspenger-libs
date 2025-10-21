package no.nav.tiltakspenger.libs.tiltak
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.DeltakerStatusDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.GjennomføringDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakType
import java.time.LocalDate

data class TiltakTilSaksbehandlingDTO(
    val id: String,
    // TODO Nullable frem til de gamle feltene ovenfor er faset ut i alle applikasjoner
    val gjennomforing: GjennomføringDTO? = null,
    @Deprecated("Bruk gjennomføring.id")
    val gjennomføringId: String?,
    val deltakelseFom: LocalDate?,
    val deltakelseTom: LocalDate?,
    @Deprecated("Bruk gjennomføring.typeNavn")
    val typeNavn: String,
    @Deprecated("Bruk gjennomføring.typeKode")
    val typeKode: TiltakType,
    val deltakelseStatus: DeltakerStatusDTO,
    val deltakelsePerUke: Float?,
    val deltakelseProsent: Float?,
    val kilde: String,
)
