package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import no.nav.tiltakspenger.libs.common.Tilknytningstittel
import no.nav.tiltakspenger.libs.common.Virksomhetsnavn
import java.time.LocalDate
import java.time.LocalDateTime

val testStatusOpprettet: LocalDateTime = LocalDateTime.of(2026, 2, 27, 12, 0)

val testStart: LocalDate = LocalDate.of(2026, 3, 2)

val testSlutt: LocalDate = LocalDate.of(2026, 6, 30)

/**
 * Bygger en deltakelse gjennom fabrikken, med alle felt fylt eksplisitt — varianten følger av inputen, akkurat som i produksjon.
 * Til tester i modulen, i konsumentene og i skyggekjøringen: bygg gjennom denne i stedet for å kopiere konstruktørkall, så følger testene med når typen endres.
 */
fun testdeltakelse(
    id: String = "TA1234567",
    kildestatus: Kildestatus = Kometstatus.Kjent(Kometstatus.Type.DELTAR, årsak = null, opprettet = testStatusOpprettet),
    tiltakstype: Tiltakstype = Tiltakstype.SomGirRett(tiltakskodeFraKilden = "INDOPPFAG", tiltakstype = TiltakstypeSomGirRett.OPPFØLGING),
    fraOgMed: LocalDate? = testStart,
    tilOgMed: LocalDate? = testSlutt,
) = tiltaksdeltakelse(
    id = EksternDeltakelseId(id),
    kildestatus = kildestatus,
    tiltakstype = tiltakstype,
    tiltakstypenavn = "Oppfølging",
    tittel = Tilknytningstittel("Oppfølging hos Arrangør AS"),
    arrangør = Arrangør(hovedenhet = Virksomhetsnavn("Arrangør AS"), underenhet = null),
    omfang = Deltakelsesomfang(deltakelsesprosent = 60f, dagerPerUke = 3f, deltidsprosentPåGjennomføring = null),
    fraOgMed = fraOgMed,
    tilOgMed = tilOgMed,
    gjennomføringId = null,
)
