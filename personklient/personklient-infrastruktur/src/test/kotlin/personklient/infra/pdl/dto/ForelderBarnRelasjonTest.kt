package no.nav.tiltakspenger.libs.personklient.infra.pdl.dto

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.FnrGenerator
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import org.junit.jupiter.api.Test

internal class ForelderBarnRelasjonTest {
    private val nå = nå(fixedClock)
    private val fnr = FnrGenerator().generer().verdi
    private val annetFnr = FnrGenerator(start = 1).generer().verdi
    private val farFnr = FnrGenerator(start = 2).generer().verdi
    private val medmorFnr = FnrGenerator(start = 3).generer().verdi

    private fun relasjon(
        rolle: ForelderBarnRelasjonRolle,
        ident: String? = null,
    ) = ForelderBarnRelasjon(
        relatertPersonsIdent = ident,
        relatertPersonsRolle = rolle,
        minRolleForPerson = ForelderBarnRelasjonRolle.MOR,
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
            relasjon(ForelderBarnRelasjonRolle.BARN, ident = fnr),
            relasjon(ForelderBarnRelasjonRolle.BARN, ident = fnr),
            relasjon(ForelderBarnRelasjonRolle.BARN, ident = annetFnr),
            // Barn uten ident og andre roller enn BARN skal ikke med.
            relasjon(ForelderBarnRelasjonRolle.BARN, ident = null),
            relasjon(ForelderBarnRelasjonRolle.FAR, ident = farFnr),
            relasjon(ForelderBarnRelasjonRolle.MEDMOR, ident = medmorFnr),
        )

        relasjoner.toIdenterForBarnIFolkeregisteret() shouldBe listOf(fnr, annetFnr)
    }

    @Test
    fun `tomme lister gir tomme lister`() {
        emptyList<ForelderBarnRelasjon>().toIdenterForBarnIFolkeregisteret() shouldBe emptyList()
    }
}
