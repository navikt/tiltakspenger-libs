package no.nav.tiltakspenger.libs.personklient.pdl.dto

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError
import org.junit.jupiter.api.Test
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering as AdressebeskyttelseGraderingDto

internal class AdressebeskyttelseTest {
    private val nå = nå(fixedClock)

    private fun adressebeskyttelse(
        gradering: AdressebeskyttelseGradering,
        master: String,
        kilde: String = Kilde.FREG,
        registrert: java.time.LocalDateTime? = nå,
        ajourholdstidspunkt: java.time.LocalDateTime? = nå,
    ) = Adressebeskyttelse(
        gradering = gradering,
        metadata = EndringsMetadata(
            master = master,
            endringer = listOf(
                Endring(
                    kilde = kilde,
                    registrert = registrert,
                    systemkilde = "systemkilde",
                    registrertAv = "registrertAv",
                    type = "OPPRETT",
                ),
            ),
        ),
        folkeregistermetadata = FolkeregisterMetadata(ajourholdstidspunkt = ajourholdstidspunkt),
    )

    @Test
    fun `tom liste er ugradert`() {
        avklarGradering(emptyList()) shouldBe AdressebeskyttelseGradering.UGRADERT.right()
    }

    @Test
    fun `nyeste dokumenterte gradering vinner`() {
        val eldst = adressebeskyttelse(
            gradering = AdressebeskyttelseGradering.UGRADERT,
            master = Kilde.FREG,
            ajourholdstidspunkt = nå.minusDays(1),
        )
        val nyest = adressebeskyttelse(
            gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            master = Kilde.FREG,
            ajourholdstidspunkt = nå,
        )

        avklarGradering(listOf(eldst, nyest)) shouldBe AdressebeskyttelseGradering.STRENGT_FORTROLIG.right()
    }

    @Test
    fun `graderinger brukeren selv har meldt inn teller ikke`() {
        val fraBrukerSelv = adressebeskyttelse(
            gradering = AdressebeskyttelseGradering.FORTROLIG,
            master = Kilde.PDL,
            kilde = Kilde.BRUKER_SELV,
        )

        avklarGradering(listOf(fraBrukerSelv)) shouldBe FellesPersonklientError.AdressebeskyttelseKunneIkkeAvklares.left()
    }

    /**
     * Endringstidspunktet hentes fra folkeregistermetadataen når mastern er FREG, ellers fra nyeste endring.
     * Her er PDL master uten registrert endringstidspunkt, så sorteringen faller tilbake på null uten å feile.
     */
    @Test
    fun `gradering med PDL som master sorteres på nyeste endring`() {
        val utenTidspunkt = adressebeskyttelse(
            gradering = AdressebeskyttelseGradering.FORTROLIG,
            master = Kilde.PDL,
            registrert = null,
        )

        avklarGradering(listOf(utenTidspunkt)) shouldBe AdressebeskyttelseGradering.FORTROLIG.right()
    }

    /**
     * Endringstidspunktet er null både når folkeregistermetadataen mangler på en FREG-mastret gradering og når PDL-mastret gradering ikke har noen endringer.
     */
    @Test
    fun `gradering uten endringstidspunkt avklares fortsatt`() {
        val fregUtenFolkeregistermetadata = Adressebeskyttelse(
            gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            metadata = EndringsMetadata(master = Kilde.FREG),
            folkeregistermetadata = null,
        )
        val pdlUtenEndringer = Adressebeskyttelse(
            gradering = AdressebeskyttelseGradering.FORTROLIG,
            metadata = EndringsMetadata(master = Kilde.PDL),
        )

        avklarGradering(listOf(fregUtenFolkeregistermetadata)) shouldBe AdressebeskyttelseGradering.STRENGT_FORTROLIG.right()
        avklarGradering(listOf(pdlUtenEndringer)) shouldBe AdressebeskyttelseGradering.FORTROLIG.right()
    }

    @Test
    fun `graderingene mapper til person-dtoen`() {
        AdressebeskyttelseGradering.entries.map { it.toPersonDto(it) } shouldBe listOf(
            AdressebeskyttelseGraderingDto.STRENGT_FORTROLIG_UTLAND,
            AdressebeskyttelseGraderingDto.STRENGT_FORTROLIG,
            AdressebeskyttelseGraderingDto.FORTROLIG,
            AdressebeskyttelseGraderingDto.UGRADERT,
        )
    }

    @Test
    fun `graderingene svarer på hva de er`() {
        AdressebeskyttelseGradering.FORTROLIG.erFortrolig() shouldBe true
        AdressebeskyttelseGradering.STRENGT_FORTROLIG.erFortrolig() shouldBe false
        AdressebeskyttelseGradering.STRENGT_FORTROLIG.erStrengtFortrolig() shouldBe true
        AdressebeskyttelseGradering.UGRADERT.erStrengtFortrolig() shouldBe false
        AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND.erStrengtFortroligUtland() shouldBe true
        AdressebeskyttelseGradering.UGRADERT.erStrengtFortroligUtland() shouldBe false
    }
}
