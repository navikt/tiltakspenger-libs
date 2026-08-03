package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet

import arrow.core.left
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Kometstatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Kometårsak
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class KometkoderTest {

    private val opprettet = LocalDateTime.of(2026, 1, 1, 12, 0)

    @Test
    fun `komet-tabellen er pinnet`() {
        kometkoderSomGirRett shouldBe mapOf(
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
        kjenteKometkoderUtenRett shouldBe setOf(
            "TILRETTELAGT_ARBEID_ORDINAER",
            "VARIG_TILRETTELAGT_ARBEID_SKJERMET",
        )
    }

    @Test
    fun `tabellene er disjunkte og har pinnet størrelse`() {
        kometkoderSomGirRett.size shouldBe 16
        kjenteKometkoderUtenRett.size shouldBe 2
        (kometkoderSomGirRett.keys intersect kjenteKometkoderUtenRett).shouldBeEmpty()
    }

    @Test
    fun `alle kjente komet-statuser blir Kjent`() {
        Kometstatus.Type.entries.forEach { type ->
            kometstatus(type.name, null, opprettet).getOrFail() shouldBe Kometstatus.Kjent(type, null, opprettet)
        }
    }

    @Test
    fun `komet-statusen bærer kjent årsak`() {
        kometstatus("HAR_SLUTTET", "FATT_JOBB", opprettet).getOrFail() shouldBe
            Kometstatus.Kjent(Kometstatus.Type.HAR_SLUTTET, Kometårsak.Kjent(Kometstatus.Årsak.FATT_JOBB), opprettet)
    }

    @Test
    fun `alle kjente komet-årsaker blir Kjent`() {
        Kometstatus.Årsak.entries.forEach { årsak ->
            kometårsak(årsak.name).getOrFail() shouldBe Kometårsak.Kjent(årsak)
        }
    }

    @Test
    fun `ukjent komet-status bærer årsaken og tidspunktet`() {
        kometstatus("HELT_NY_STATUS", "HELT_NY_AARSAK", opprettet).getOrFail() shouldBe
            Kometstatus.Ukjent("HELT_NY_STATUS", Kometårsak.Ukjent("HELT_NY_AARSAK"), opprettet)
    }

    @Test
    fun `blank komet-status feiler`() {
        kometstatus(" ", null, opprettet) shouldBe UgyldigKontraktsverdi("Blank deltakerstatus fra Komet kan ikke bæres som ukjent kildeverdi").left()
    }

    @Test
    fun `blank komet-årsak feiler`() {
        kometstatus("DELTAR", " ", opprettet) shouldBe UgyldigKontraktsverdi("Blank årsak fra Komet kan ikke bæres som ukjent kildeverdi").left()
    }
}
