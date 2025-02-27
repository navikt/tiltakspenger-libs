package no.nav.tiltakspenger.libs.tiltak

import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.DeltakerStatusDTO

enum class KometDeltakerStatusType {
    UTKAST_TIL_PAMELDING,
    AVBRUTT_UTKAST,
    VENTER_PA_OPPSTART,
    DELTAR,
    HAR_SLUTTET,
    IKKE_AKTUELL,
    FEILREGISTRERT,
    SOKT_INN,
    VURDERES,
    VENTELISTE,
    AVBRUTT,
    FULLFORT,
    PABEGYNT_REGISTRERING,
}

fun KometDeltakerStatusType.toDeltakerStatusDTO(): TiltakResponsDTO.DeltakerStatusDTO = when (this) {
    KometDeltakerStatusType.UTKAST_TIL_PAMELDING,
    KometDeltakerStatusType.PABEGYNT_REGISTRERING,
    -> DeltakerStatusDTO.PABEGYNT_REGISTRERING

    KometDeltakerStatusType.AVBRUTT_UTKAST,
    KometDeltakerStatusType.IKKE_AKTUELL,
    -> DeltakerStatusDTO.IKKE_AKTUELL

    KometDeltakerStatusType.VENTER_PA_OPPSTART -> DeltakerStatusDTO.VENTER_PA_OPPSTART
    KometDeltakerStatusType.DELTAR -> DeltakerStatusDTO.DELTAR
    KometDeltakerStatusType.HAR_SLUTTET -> DeltakerStatusDTO.HAR_SLUTTET
    KometDeltakerStatusType.FEILREGISTRERT -> DeltakerStatusDTO.FEILREGISTRERT
    KometDeltakerStatusType.SOKT_INN -> DeltakerStatusDTO.SOKT_INN
    KometDeltakerStatusType.VURDERES -> DeltakerStatusDTO.VURDERES
    KometDeltakerStatusType.VENTELISTE -> DeltakerStatusDTO.VENTELISTE
    KometDeltakerStatusType.AVBRUTT -> DeltakerStatusDTO.AVBRUTT
    KometDeltakerStatusType.FULLFORT -> DeltakerStatusDTO.FULLFORT
}
