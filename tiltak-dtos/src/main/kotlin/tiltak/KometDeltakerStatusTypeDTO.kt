package no.nav.tiltakspenger.libs.tiltak

import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.DeltakerStatusDTO

enum class KometDeltakerStatusTypeDTO {
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

fun KometDeltakerStatusTypeDTO.toDeltakerStatusDTO(): TiltakResponsDTO.DeltakerStatusDTO = when (this) {
    KometDeltakerStatusTypeDTO.UTKAST_TIL_PAMELDING,
    KometDeltakerStatusTypeDTO.PABEGYNT_REGISTRERING,
    -> DeltakerStatusDTO.PABEGYNT_REGISTRERING

    KometDeltakerStatusTypeDTO.AVBRUTT_UTKAST,
    KometDeltakerStatusTypeDTO.IKKE_AKTUELL,
    -> DeltakerStatusDTO.IKKE_AKTUELL

    KometDeltakerStatusTypeDTO.VENTER_PA_OPPSTART -> DeltakerStatusDTO.VENTER_PA_OPPSTART

    KometDeltakerStatusTypeDTO.DELTAR -> DeltakerStatusDTO.DELTAR

    KometDeltakerStatusTypeDTO.HAR_SLUTTET -> DeltakerStatusDTO.HAR_SLUTTET

    KometDeltakerStatusTypeDTO.FEILREGISTRERT -> DeltakerStatusDTO.FEILREGISTRERT

    KometDeltakerStatusTypeDTO.SOKT_INN -> DeltakerStatusDTO.SOKT_INN

    KometDeltakerStatusTypeDTO.VURDERES -> DeltakerStatusDTO.VURDERES

    KometDeltakerStatusTypeDTO.VENTELISTE -> DeltakerStatusDTO.VENTELISTE

    KometDeltakerStatusTypeDTO.AVBRUTT -> DeltakerStatusDTO.AVBRUTT

    KometDeltakerStatusTypeDTO.FULLFORT -> DeltakerStatusDTO.FULLFORT
}
