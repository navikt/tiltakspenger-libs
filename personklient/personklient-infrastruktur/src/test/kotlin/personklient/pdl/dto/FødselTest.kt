package no.nav.tiltakspenger.libs.personklient.pdl.dto

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class FødselTest {
    private val nå = nå(fixedClock)

    private fun fødsel(
        foedselsdato: LocalDate,
        master: String,
        ajourholdstidspunkt: LocalDateTime? = nå,
    ) = Fødsel(
        foedselsdato = foedselsdato,
        metadata = EndringsMetadata(
            master = master,
            endringer = listOf(
                Endring(
                    kilde = master,
                    registrert = ajourholdstidspunkt,
                    systemkilde = "systemkilde",
                    registrertAv = "registrertAv",
                    type = "OPPRETT",
                ),
            ),
        ),
        folkeregistermetadata = FolkeregisterMetadata(ajourholdstidspunkt = ajourholdstidspunkt),
    )

    @Test
    fun `fødsel fra freg vinner over nyere fødsel fra PDL`() {
        val fraFreg = fødsel(LocalDate.of(1990, 1, 1), Kilde.FREG, nå.minusDays(10))
        val fraPdl = fødsel(LocalDate.of(1991, 2, 2), Kilde.PDL, nå)

        avklarFødsel(listOf(fraPdl, fraFreg)) shouldBe fraFreg.right()
    }

    @Test
    fun `uten freg velges den nyest registrerte`() {
        val eldst = fødsel(LocalDate.of(1990, 1, 1), Kilde.PDL, nå.minusDays(10))
        val nyest = fødsel(LocalDate.of(1991, 2, 2), Kilde.PDL, nå)

        avklarFødsel(listOf(eldst, nyest)) shouldBe nyest.right()
    }

    @Test
    fun `ingen fødsler kan ikke avklares`() {
        avklarFødsel(emptyList()) shouldBe FellesPersonklientError.FødselKunneIkkeAvklares.left()
    }

    @Test
    fun `freg-sjekken bryr seg ikke om store og små bokstaver`() {
        "freg".isFreg() shouldBe true
        "FREG".isFreg() shouldBe true
        Kilde.PDL.isFreg() shouldBe false
    }
}
