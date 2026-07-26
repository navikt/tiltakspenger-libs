package no.nav.tiltakspenger.libs.personklient.pdl.dto

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.person.BarnUtenFolkeregisteridentifikator
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class ForelderBarnRelasjonTest {
    private val nå = nå(fixedClock)

    private fun relasjon(
        rolle: ForelderBarnRelasjonRolle,
        ident: String? = null,
        utenFolkeregisteridentifikator: RelatertBiPerson? = null,
    ) = ForelderBarnRelasjon(
        relatertPersonsIdent = ident,
        relatertPersonsRolle = rolle,
        minRolleForPerson = ForelderBarnRelasjonRolle.MOR,
        relatertPersonUtenFolkeregisteridentifikator = utenFolkeregisteridentifikator,
        metadata = EndringsMetadata(
            master = Kilde.FREG,
            endringer = listOf(
                Endring(
                    kilde = Kilde.FREG,
                    registrert = nå,
                    systemkilde = "systemkilde",
                    registrertAv = "registrertAv",
                    type = "OPPRETT",
                ),
            ),
        ),
        folkeregistermetadata = FolkeregisterMetadata(ajourholdstidspunkt = nå),
    )

    @Test
    fun `plukker ut unike identer for barn i folkeregisteret`() {
        val relasjoner = listOf(
            relasjon(ForelderBarnRelasjonRolle.BARN, ident = "12345678910"),
            relasjon(ForelderBarnRelasjonRolle.BARN, ident = "12345678910"),
            relasjon(ForelderBarnRelasjonRolle.BARN, ident = "10987654321"),
            // Barn uten ident og andre roller enn BARN skal ikke med.
            relasjon(ForelderBarnRelasjonRolle.BARN, ident = null),
            relasjon(ForelderBarnRelasjonRolle.FAR, ident = "11111111111"),
            relasjon(ForelderBarnRelasjonRolle.MEDMOR, ident = "22222222222"),
        )

        relasjoner.toIdenterForBarnIFolkeregisteret() shouldBe listOf("12345678910", "10987654321")
    }

    @Test
    fun `mapper barn uten folkeregisteridentifikator`() {
        val relasjoner = listOf(
            relasjon(
                ForelderBarnRelasjonRolle.BARN,
                utenFolkeregisteridentifikator = RelatertBiPerson(
                    navn = Personnavn(fornavn = "Barn", mellomnavn = "Uten", etternavn = "Ident"),
                    foedselsdato = LocalDate.of(2020, 3, 4),
                    statsborgerskap = "NOR",
                ),
            ),
            // Uten navn skal navnefeltene bli null, ikke feile.
            relasjon(
                ForelderBarnRelasjonRolle.BARN,
                utenFolkeregisteridentifikator = RelatertBiPerson(foedselsdato = LocalDate.of(2021, 5, 6)),
            ),
            // Barn med ident og relasjoner som ikke er barn skal ikke med.
            relasjon(ForelderBarnRelasjonRolle.BARN, ident = "12345678910"),
            relasjon(
                ForelderBarnRelasjonRolle.MOR,
                utenFolkeregisteridentifikator = RelatertBiPerson(statsborgerskap = "SWE"),
            ),
        )

        relasjoner.toBarnUtenforFolkeregisteret() shouldBe listOf(
            BarnUtenFolkeregisteridentifikator(
                fornavn = "Barn",
                mellomnavn = "Uten",
                etternavn = "Ident",
                fødselsdato = LocalDate.of(2020, 3, 4),
                statsborgerskap = "NOR",
            ),
            BarnUtenFolkeregisteridentifikator(
                fornavn = null,
                mellomnavn = null,
                etternavn = null,
                fødselsdato = LocalDate.of(2021, 5, 6),
                statsborgerskap = null,
            ),
        )
    }

    @Test
    fun `tomme lister gir tomme lister`() {
        emptyList<ForelderBarnRelasjon>().toIdenterForBarnIFolkeregisteret() shouldBe emptyList()
        emptyList<ForelderBarnRelasjon>().toBarnUtenforFolkeregisteret() shouldBe emptyList()
    }
}
