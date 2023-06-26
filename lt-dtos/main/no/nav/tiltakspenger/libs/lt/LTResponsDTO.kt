package no.nav.tiltakspenger.libs.lt

import java.time.LocalDateTime

data class LTResponsDTO(
    val status: TiltaksstatusV1DTO? = null,
    val feil: FeilmeldingDTO? = null,
) {

    enum class FeilmeldingDTO(val melding: String) {
        UkjentFeil("Ukjent feil"),
    }

    data class TiltaksstatusV1DTO(
        val hendelseType: Hendelsetype,
        val avtaleStatus: Avtalestatus,
        val deltakerFnr: String,
        val tiltakstype: Tiltakstype,
        val opprettetTidspunkt: LocalDateTime,
        val tilskuddPeriode: List<TilskuddPeriode>,
        val versjon: Int,
    )

    data class TilskuddPeriode(
        val startDato: LocalDateTime,
        val sluttDato: LocalDateTime,
    )

    enum class Avtalestatus {
        ANNULLERT,
        AVBRUTT,
        PÅBEGYNT,
        MANGLER_GODKJENNING,
        KLAR_FOR_OPPSTART,
        GJENNOMFØRES,
        AVSLUTTET,
    }

    enum class Hendelsetype {
        OPPRETTET,
        GODKJENT_AV_ARBEIDSGIVER,
        GODKJENT_AV_VEILEDER,
        GODKJENT_AV_DELTAKER,
        SIGNERT_AV_MENTOR,
        GODKJENT_PAA_VEGNE_AV,
        GODKJENT_PAA_VEGNE_AV_DELTAKER_OG_ARBEIDSGIVER,
        GODKJENT_PAA_VEGNE_AV_ARBEIDSGIVER,
        GODKJENNINGER_OPPHEVET_AV_ARBEIDSGIVER,
        GODKJENNINGER_OPPHEVET_AV_VEILEDER,
        DELT_MED_DELTAKER,
        DELT_MED_ARBEIDSGIVER,
        DELT_MED_MENTOR,
        ENDRET,
        AVBRUTT,
        ANNULLERT,
        LÅST_OPP,
        GJENOPPRETTET,
        OPPRETTET_AV_ARBEIDSGIVER,
        NY_VEILEDER,
        AVTALE_FORDELT,
        TILSKUDDSPERIODE_AVSLATT,
        TILSKUDDSPERIODE_GODKJENT,
        AVTALE_FORKORTET,
        AVTALE_FORLENGET,
        MÅL_ENDRET,
        INKLUDERINGSTILSKUDD_ENDRET,
        OM_MENTOR_ENDRET,
        TILSKUDDSBEREGNING_ENDRET,
        KONTAKTINFORMASJON_ENDRET,
        STILLINGSBESKRIVELSE_ENDRET,
        OPPFØLGING_OG_TILRETTELEGGING_ENDRET,
        AVTALE_INNGÅTT,
        REFUSJON_KLAR,
        REFUSJON_KLAR_REVARSEL,
        REFUSJON_FRIST_FORLENGET,
        REFUSJON_KORRIGERT,
        VARSLER_SETT,
        AVTALE_SLETTET,
        GODKJENT_FOR_ETTERREGISTRERING,
        FJERNET_ETTERREGISTRERING,
        STATUSENDRING,
    }

    enum class Tiltakstype {
        ARBEIDSTRENING,
        MIDLERTIDIG_LONNSTILSKUDD,
        VARIG_LONNSTILSKUDD,
        MENTOR,
        INKLUDERINGSTILSKUDD,
        SOMMERJOBB,
    }
}
