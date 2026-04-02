package no.nav.tiltakspenger.libs.personklient.pdl.dto

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError
import org.junit.jupiter.api.Test

internal class NavnTest {
    private val nå = nå(fixedClock)

    @Test
    fun `skal ikke kunne avklare navn når det kommer fra bruker selv`() {
        val avklartNavn = avklarNavn(
            listOf(
                Navn(
                    fornavn = "asdsa",
                    etternavn = "adssa",
                    metadata = EndringsMetadata(
                        master = Kilde.PDL,
                        endringer = listOf(
                            Endring(
                                kilde = Kilde.BRUKER_SELV,
                                registrert = nå,
                                systemkilde = "lol",
                                registrertAv = "qwe",
                                type = "OPPRETT",
                            ),
                        ),
                    ),
                    folkeregistermetadata = FolkeregisterMetadata(
                        kilde = Kilde.PDL,
                        sekvens = 1,
                        gyldighetstidspunkt = nå,
                        ajourholdstidspunkt = nå,
                        aarsak = null,
                        opphoerstidspunkt = null,
                    ),
                ),
            ),
        )
        avklartNavn.isLeft() shouldBe true
        avklartNavn shouldBe FellesPersonklientError.NavnKunneIkkeAvklares.left()
    }

    @Test
    fun `skal kunne avklare navn når det kommer fra freg`() {
        val navn = Navn(
            fornavn = "asdsa",
            etternavn = "adssa",
            metadata = EndringsMetadata(
                master = Kilde.PDL,
                endringer = listOf(
                    Endring(
                        kilde = Kilde.FREG,
                        registrert = nå,
                        systemkilde = "lol",
                        registrertAv = "qwe",
                        type = "OPPRETT",
                    ),
                ),
            ),
            folkeregistermetadata = FolkeregisterMetadata(
                kilde = Kilde.FREG,
                sekvens = 1,
                gyldighetstidspunkt = nå,
                ajourholdstidspunkt = nå,
                aarsak = null,
                opphoerstidspunkt = null,
            ),
        )
        val avklartNavn = avklarNavn(
            listOf(navn),
        )
        avklartNavn shouldBe navn.right()
    }
}
