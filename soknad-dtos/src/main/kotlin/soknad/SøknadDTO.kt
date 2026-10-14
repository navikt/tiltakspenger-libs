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
    val saksnummer: String,
    /**
     * Søkerens svar på om hen vil søke barnetillegg.
     * Er null for søknader fra før søknaden spurte om dette, da var det barnelistene som avgjorde om det var søkt.
     */
    val harSøktOmBarnetillegg: JaNeiSpmDTO?,
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
    val visningsnavn: String?,
    val typeKode: String,
    val typeNavn: String,
)

data class BarnetilleggDTO(
    // TODO - nullable for å fungere med dagens løsning til vi får merget inn andre repos
    val fnr: String?,
    val fødselsdato: LocalDate?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
    /**
     * Ja når barnet oppholder seg i Norge eller et annet EØS-land i tiltaksperioden.
     * Når [oppholdssted] er satt, er dette feltet avledet fra det.
     */
    val oppholderSegIEØS: JaNeiSpmDTO,
    /**
     * Hvor barnet oppholder seg i tiltaksperioden.
     * Er null for søknader fra før søknaden spurte om dette, da var svaret bare ja/nei på [oppholderSegIEØS].
     */
    val oppholdssted: OppholdsstedDTO?,
)

enum class OppholdsstedDTO {
    NORGE,
    ANNET_EØS_LAND,
    IKKE_EØS_LAND,
}

enum class AdressebeskyttelseDTO {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG,
    UGRADERT,
}

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
