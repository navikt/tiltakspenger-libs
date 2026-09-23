package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Arenastatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.tiltakstype

// Kodene fra Arena: tiltakskodetabellene (flyttet fra toTiltakstypeSomGirRett i tiltak-dtos) og statusmappingen.
// En kjent statuskode blir Kjent, en ukjent blir kildens egen Ukjent-variant med kontraktens kode i behold — aldri valueOf, aldri kast; blank feiler som UgyldigKontraktsverdi.
// Innholdet er paritetstestet mot dagens kjede så lenge tiltak-dtos finnes, og literal-pinnet i egne tester.

/**
 * Arena-kodene som gir rett.
 */
internal val arenakoderSomGirRett: Map<String, TiltakstypeSomGirRett> = mapOf(
    "ARBEIDSMARKEDSOPPLAERING" to TiltakstypeSomGirRett.ARBEIDSMARKEDSOPPLAERING,
    "ARBFORB" to TiltakstypeSomGirRett.ARBEIDSFORBEREDENDE_TRENING,
    "ARBRRHDAG" to TiltakstypeSomGirRett.ARBEIDSRETTET_REHABILITERING,
    "ARBTREN" to TiltakstypeSomGirRett.ARBEIDSTRENING,
    "AVKLARAG" to TiltakstypeSomGirRett.AVKLARING,
    "DIGIOPPARB" to TiltakstypeSomGirRett.DIGITAL_JOBBKLUBB,
    "ENKELAMO" to TiltakstypeSomGirRett.ENKELTPLASS_AMO,
    "ENKFAGYRKE" to TiltakstypeSomGirRett.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG,
    "FAG_OG_YRKESOPPLAERING" to TiltakstypeSomGirRett.FAG_OG_YRKESOPPLAERING,
    "FORSOPPLEV" to TiltakstypeSomGirRett.FORSØK_OPPLÆRING_LENGRE_VARIGHET,
    "GRUFAGYRKE" to TiltakstypeSomGirRett.GRUPPE_VGS_OG_HØYERE_YRKESFAG,
    "GRUPPEAMO" to TiltakstypeSomGirRett.GRUPPE_AMO,
    "HOYERE_YRKESFAGLIG_UTDANNING" to TiltakstypeSomGirRett.HOYERE_YRKESFAGLIG_UTDANNING,
    "HOYEREUTD" to TiltakstypeSomGirRett.HØYERE_UTDANNING,
    "INDJOBSTOT" to TiltakstypeSomGirRett.INDIVIDUELL_JOBBSTØTTE,
    "INDOPPFAG" to TiltakstypeSomGirRett.OPPFØLGING,
    "IPSUNG" to TiltakstypeSomGirRett.INDIVIDUELL_KARRIERESTØTTE_UNG,
    "JOBBK" to TiltakstypeSomGirRett.JOBBKLUBB,
    "NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV" to TiltakstypeSomGirRett.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
    "STUDIESPESIALISERING" to TiltakstypeSomGirRett.STUDIESPESIALISERING,
    "UTVAOONAV" to TiltakstypeSomGirRett.UTVIDET_OPPFØLGING_I_NAV,
    "UTVOPPFOPL" to TiltakstypeSomGirRett.UTVIDET_OPPFØLGING_I_OPPLÆRING,
)

/**
 * Resten av Arena-vokabularet vi kjenner — dagens `TiltakTypeDTO` minus kodene som gir rett.
 * Merk at langformen `JOBBKLUBB` står her med vilje: det er Komet-navnet som havnet i Arena-vokabularet, og bare Arenas egen `JOBBK` gir rett — paritet med dagens kjede.
 */
internal val kjenteArenakoderUtenRett: Set<String> = setOf(
    "ABIST",
    "ABOPPF",
    "ABTBOPPF",
    "ABUOPPF",
    "AMBF1",
    "AMBF2",
    "AMBF3",
    "AMO",
    "AMOB",
    "AMOE",
    "AMOY",
    "ANNUTDANN",
    "ARBDOGNSM",
    "ARBRDAGSM",
    "ARBRRDOGN",
    "ARBRRHBAG",
    "ARBRRHBSM",
    "ASV",
    "ATG",
    "AVKLARKV",
    "AVKLARSP",
    "AVKLARSV",
    "AVKLARUS",
    "BIA",
    "BIO",
    "BREVKURS",
    "DIVTILT",
    "EKSPEBIST",
    "ETAB",
    "FIREARIG_LONNSTILSKUDD",
    "FLEKSJOBB",
    "FORSAMOENK",
    "FORSAMOGRU",
    "FORSFAGENK",
    "FORSFAGGRU",
    "FORSHOYUTD",
    "FUNKSJASS",
    "GRUNNSKOLE",
    "HOYSKOLE",
    "INDOPPFOLG",
    "INDOPPFSP",
    "INDOPPRF",
    "INKLUTILS",
    "INST_S",
    "ITGRTILS",
    "JOBBBONUS",
    "JOBBFOKUS",
    "JOBBKLUBB",
    "JOBBSKAP",
    "KAT",
    "KURS",
    "LONNTIL",
    "LONNTILAAP",
    "LONNTILL",
    "LONNTILS",
    "MENTOR",
    "MIDLONTIL",
    "NETTAMO",
    "NETTKURS",
    "NYTEST",
    "OPPLT2AAR",
    "PRAKSKJERM",
    "PRAKSORD",
    "PV",
    "REAKTUFOR",
    "REFINO",
    "SPA",
    "STATLAERL",
    "SUPPEMP",
    "SYSSLANG",
    "SYSSOFF",
    "TESTING",
    "TIDSUBLONN",
    "TILPERBED",
    "TILRETTEL",
    "TILRTILSK",
    "TILSJOBB",
    "UFØREPENLØ",
    "UTBHLETTPS",
    "UTBHPSLD",
    "UTBHSAMLI",
    "UTDPERMVIK",
    "UTDYRK",
    "VALS",
    "VARLONTIL",
    "VASV",
    "VATIAROR",
    "VIDRSKOLE",
    "VIKARBLED",
    "VV",
    "YHEMMOFF",
)

internal fun arenaTiltakstype(tiltakskode: String): Either<UgyldigKontraktsverdi, Tiltakstype> =
    tiltakstype(tiltakskode, arenakoderSomGirRett, kjenteArenakoderUtenRett, "Arena")

internal fun arenastatus(kodeIKontrakten: String): Either<UgyldigKontraktsverdi, Arenastatus> {
    val kjent = Arenastatus.Type.entries.find { it.name == kodeIKontrakten }
    return when {
        kjent != null -> Arenastatus.Kjent(kjent).right()
        kodeIKontrakten.isBlank() -> UgyldigKontraktsverdi("Blank deltakerstatus fra Arena kan ikke bæres som ukjent kildeverdi").left()
        else -> Arenastatus.Ukjent(kodeIKontrakten).right()
    }
}
