package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena

import arrow.core.left
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import no.nav.tiltakspenger.libs.tiltak.toTiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Arenastatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import org.junit.jupiter.api.Test

class ArenakoderTest {

    @Test
    fun `arena-tabellen som gir rett er pinnet`() {
        arenakoderSomGirRett shouldBe mapOf(
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
    }

    /**
     * Paritetsvakt mot kjeden som avvikles: Arena-vokabularet skal være nøyaktig `TiltakTypeDTO`, og rett-utfallet per kode skal matche `toTiltakstypeSomGirRett`.
     * Testen slettes sammen med `tiltak-dtos`.
     */
    @Test
    fun `arena-vokabularet og rett-utfallet matcher dagens kjede i tiltak-dtos`() {
        val vokabular = TiltakResponsDTO.TiltakTypeDTO.entries

        (arenakoderSomGirRett.keys + kjenteArenakoderUtenRett) shouldBe vokabular.map { it.name }.toSet()

        vokabular.forEach { kode ->
            kode.toTiltakstypeSomGirRett().fold(
                { (kode.name in kjenteArenakoderUtenRett) shouldBe true },
                { rett -> arenakoderSomGirRett[kode.name]?.name shouldBe rett.name },
            )
        }
    }

    @Test
    fun `tabellene er disjunkte og har pinnet størrelse`() {
        arenakoderSomGirRett.size shouldBe 22
        kjenteArenakoderUtenRett.size shouldBe 91
        (arenakoderSomGirRett.keys intersect kjenteArenakoderUtenRett).shouldBeEmpty()
    }

    @Test
    fun `alle kjente arena-statuser blir Kjent`() {
        Arenastatus.Type.entries.forEach { type ->
            arenastatus(type.name).getOrFail() shouldBe Arenastatus.Kjent(type)
        }
    }

    @Test
    fun `ukjent arena-status bæres ordrett`() {
        arenastatus("HELT_NY_STATUS").getOrFail() shouldBe Arenastatus.Ukjent("HELT_NY_STATUS")
    }

    @Test
    fun `blank arena-status feiler`() {
        arenastatus(" ") shouldBe UgyldigKontraktsverdi("Blank deltakerstatus fra Arena kan ikke bæres som ukjent kildeverdi").left()
    }
}
