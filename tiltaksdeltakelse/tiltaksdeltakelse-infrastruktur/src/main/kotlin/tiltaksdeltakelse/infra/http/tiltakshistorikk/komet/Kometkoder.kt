package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Kometstatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Kometårsak
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.tiltakstype
import java.time.LocalDateTime

// Kodene fra Komet: tiltakskodetabellene (flyttet fra TiltakskodeDto i tiltakspenger-tiltak, men uten dagens omvei om Arena-koden) og statusmappingen.
// En kjent kode blir Kjent, en ukjent blir kildens egen Ukjent-variant med kontraktens kode i behold — aldri valueOf, aldri kast; blank feiler som UgyldigKontraktsverdi.
// Årsaken er sin egen kjent/ukjent-akse og leses uavhengig av om statustypen er kjent.
// Innholdet er literal-pinnet i egne tester.

/**
 * Komet-kodene som gir rett.
 */
internal val kometkoderSomGirRett: Map<String, TiltakstypeSomGirRett> = mapOf(
    "ARBEIDSFORBEREDENDE_TRENING" to TiltakstypeSomGirRett.ARBEIDSFORBEREDENDE_TRENING,
    "ARBEIDSMARKEDSOPPLAERING" to TiltakstypeSomGirRett.ARBEIDSMARKEDSOPPLAERING,
    "ARBEIDSRETTET_REHABILITERING" to TiltakstypeSomGirRett.ARBEIDSRETTET_REHABILITERING,
    "AVKLARING" to TiltakstypeSomGirRett.AVKLARING,
    "DIGITALT_OPPFOLGINGSTILTAK" to TiltakstypeSomGirRett.DIGITAL_JOBBKLUBB,
    "ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING" to TiltakstypeSomGirRett.ENKELTPLASS_AMO,
    "ENKELTPLASS_FAG_OG_YRKESOPPLAERING" to TiltakstypeSomGirRett.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG,
    "FAG_OG_YRKESOPPLAERING" to TiltakstypeSomGirRett.FAG_OG_YRKESOPPLAERING,
    "GRUPPE_ARBEIDSMARKEDSOPPLAERING" to TiltakstypeSomGirRett.GRUPPE_AMO,
    "GRUPPE_FAG_OG_YRKESOPPLAERING" to TiltakstypeSomGirRett.GRUPPE_VGS_OG_HØYERE_YRKESFAG,
    "HOYERE_UTDANNING" to TiltakstypeSomGirRett.HØYERE_UTDANNING,
    "HOYERE_YRKESFAGLIG_UTDANNING" to TiltakstypeSomGirRett.HOYERE_YRKESFAGLIG_UTDANNING,
    "JOBBKLUBB" to TiltakstypeSomGirRett.JOBBKLUBB,
    "NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV" to TiltakstypeSomGirRett.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
    "OPPFOLGING" to TiltakstypeSomGirRett.OPPFØLGING,
    "STUDIESPESIALISERING" to TiltakstypeSomGirRett.STUDIESPESIALISERING,
)

/**
 * Komet-kodene uten rett: varig tilrettelagt arbeid i skjermet og ordinær virksomhet (VTAO).
 */
internal val kjenteKometkoderUtenRett: Set<String> = setOf(
    "TILRETTELAGT_ARBEID_ORDINAER",
    "VARIG_TILRETTELAGT_ARBEID_SKJERMET",
)

internal fun kometTiltakstype(tiltakskode: String): Either<UgyldigKontraktsverdi, Tiltakstype> =
    tiltakstype(tiltakskode, kometkoderSomGirRett, kjenteKometkoderUtenRett, "Komet")

internal fun kometstatus(
    kodeIKontrakten: String,
    årsakskode: String?,
    opprettet: LocalDateTime,
): Either<UgyldigKontraktsverdi, Kometstatus> = either {
    val årsak = årsakskode?.let { kometårsak(it).bind() }
    val kjent = Kometstatus.Type.entries.find { it.name == kodeIKontrakten }
    when {
        kjent != null -> Kometstatus.Kjent(type = kjent, årsak = årsak, opprettet = opprettet)
        kodeIKontrakten.isBlank() -> raise(UgyldigKontraktsverdi("Blank deltakerstatus fra Komet kan ikke bæres som ukjent kildeverdi"))
        else -> Kometstatus.Ukjent(kodeIKontrakten = kodeIKontrakten, årsak = årsak, opprettet = opprettet)
    }
}

internal fun kometårsak(kodeIKontrakten: String): Either<UgyldigKontraktsverdi, Kometårsak> {
    val kjent = Kometstatus.Årsak.entries.find { it.name == kodeIKontrakten }
    return when {
        kjent != null -> Kometårsak.Kjent(kjent).right()
        kodeIKontrakten.isBlank() -> UgyldigKontraktsverdi("Blank årsak fra Komet kan ikke bæres som ukjent kildeverdi").left()
        else -> Kometårsak.Ukjent(kodeIKontrakten).right()
    }
}
