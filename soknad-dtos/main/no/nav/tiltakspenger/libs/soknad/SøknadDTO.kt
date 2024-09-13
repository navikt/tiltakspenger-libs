package no.nav.tiltakspenger.libs.soknad

import java.time.LocalDate
import java.time.LocalDateTime

data class SøknadDTO(
    val versjon: String,
    val søknadId: String,
    val journalpostId: String,
    val personopplysninger: PersonopplysningerDTO,
    val tiltak: SøknadsTiltakDTO,
    val barnetilleggPdl: List<BarnetilleggDTO>,
    val barnetilleggManuelle: List<BarnetilleggDTO>,
    val vedlegg: Int,
    val kvp: PeriodeSpmDTO,
    val intro: PeriodeSpmDTO,
    val institusjon: PeriodeSpmDTO,
    val etterlønn: JaNeiSpmDTO,
    val gjenlevendepensjon: PeriodeSpmDTO,
    val alderspensjon: FraOgMedDatoSpmDTO,
    val sykepenger: PeriodeSpmDTO,
    val supplerendeStønadAlder: PeriodeSpmDTO,
    val supplerendeStønadFlyktning: PeriodeSpmDTO,
    val jobbsjansen: PeriodeSpmDTO,
    val trygdOgPensjon: PeriodeSpmDTO,
    val opprettet: LocalDateTime,
)

data class PersonopplysningerDTO(
    val ident: String,
    val fornavn: String,
    val etternavn: String,
)

data class SøknadsTiltakDTO(
    val id: String,
    val deltakelseFom: LocalDate,
    val deltakelseTom: LocalDate,
    val arrangør: String,
    val typeKode: String,
    val typeNavn: String,
)

data class BarnetilleggDTO(
    val fødselsdato: LocalDate?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
    val oppholderSegIEØS: JaNeiSpmDTO,
)

data class JaNeiSpmDTO(
    val svar: SpmSvarDTO,
)

data class PeriodeSpmDTO(
    val svar: SpmSvarDTO,
    val fom: LocalDate?,
    val tom: LocalDate?,
)

data class FraOgMedDatoSpmDTO(
    val svar: SpmSvarDTO,
    val fom: LocalDate?,
)

enum class SpmSvarDTO {
    Nei,
    Ja,
}
