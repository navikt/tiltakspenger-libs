package no.nav.tiltakspenger.libs.arena.tiltak

import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import java.time.LocalDate

enum class ArenaDeltakerStatusType(val navn: String) {
    AKTUELL("Aktuell"),
    AVSLAG("Fått avslag"),
    DELAVB("Deltakelse avbrutt"),
    FULLF("Fullført"),
    GJENN("Gjennomføres"),
    GJENN_AVB("Gjennomføring avbrutt"),
    GJENN_AVL("Gjennomføring avlyst"),
    IKKAKTUELL("Ikke aktuell"),
    IKKEM("Ikke møtt"),
    INFOMOETE("Informasjonsmøte"),
    JATAKK("Takket ja til tilbud"),
    NEITAKK("Takket nei til tilbud"),
    TILBUD("Godkjent tiltaksplass"),
    VENTELISTE("Venteliste"),
    FEILREG("Feilregistrert"),
}

fun ArenaDeltakerStatusType.toDTO(fom: LocalDate?): TiltakResponsDTO.DeltakerStatusDTO {
    val startdatoErFremITid = fom == null || fom.isAfter(LocalDate.now())

    return when (this) {
        ArenaDeltakerStatusType.DELAVB -> TiltakResponsDTO.DeltakerStatusDTO.AVBRUTT
        ArenaDeltakerStatusType.FULLF -> TiltakResponsDTO.DeltakerStatusDTO.FULLFORT
        ArenaDeltakerStatusType.GJENN -> if (startdatoErFremITid) TiltakResponsDTO.DeltakerStatusDTO.VENTER_PA_OPPSTART else TiltakResponsDTO.DeltakerStatusDTO.DELTAR
        ArenaDeltakerStatusType.GJENN_AVB -> TiltakResponsDTO.DeltakerStatusDTO.AVBRUTT
        ArenaDeltakerStatusType.IKKEM -> TiltakResponsDTO.DeltakerStatusDTO.AVBRUTT
        ArenaDeltakerStatusType.JATAKK -> TiltakResponsDTO.DeltakerStatusDTO.DELTAR
        ArenaDeltakerStatusType.TILBUD -> TiltakResponsDTO.DeltakerStatusDTO.VENTER_PA_OPPSTART

        ArenaDeltakerStatusType.AKTUELL -> TiltakResponsDTO.DeltakerStatusDTO.SOKT_INN
        ArenaDeltakerStatusType.AVSLAG -> TiltakResponsDTO.DeltakerStatusDTO.IKKE_AKTUELL
        ArenaDeltakerStatusType.GJENN_AVL -> TiltakResponsDTO.DeltakerStatusDTO.IKKE_AKTUELL
        ArenaDeltakerStatusType.IKKAKTUELL -> TiltakResponsDTO.DeltakerStatusDTO.IKKE_AKTUELL
        ArenaDeltakerStatusType.INFOMOETE -> TiltakResponsDTO.DeltakerStatusDTO.VENTELISTE
        ArenaDeltakerStatusType.NEITAKK -> TiltakResponsDTO.DeltakerStatusDTO.IKKE_AKTUELL
        ArenaDeltakerStatusType.VENTELISTE -> TiltakResponsDTO.DeltakerStatusDTO.VENTELISTE
        ArenaDeltakerStatusType.FEILREG -> TiltakResponsDTO.DeltakerStatusDTO.FEILREGISTRERT
    }
}
