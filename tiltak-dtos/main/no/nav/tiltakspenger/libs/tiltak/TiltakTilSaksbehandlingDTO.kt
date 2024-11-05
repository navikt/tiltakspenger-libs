package no.nav.tiltakspenger.libs.tiltak

import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.DeltakerStatusDTO
import java.time.LocalDate
import java.time.LocalDateTime

data class TiltakTilSaksbehandlingDTO(
    val id: String,
    val deltakelseFom: LocalDate,
    val deltakelseTom: LocalDate,
    val gjennomføringId: String,
    val typeNavn: String,
    val typeKode: String,
    val deltakelseStatus: DeltakerStatusDTO,
    val deltakelsePerUke: Float?,
    val deltakelseProsent: Float?,
    val kilde: String,
    val registrertDato: LocalDateTime,
)
