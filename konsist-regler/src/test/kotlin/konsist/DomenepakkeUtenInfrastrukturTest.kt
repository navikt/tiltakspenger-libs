package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class DomenepakkeUtenInfrastrukturTest {
    private val scope = fixtureScope("domenepakke")

    @Test
    fun `flagger infra-underpakke, men ikke rene domenefiler`() {
        val brudd = DomenepakkeUtenInfrastruktur.bruddInfraUnderpakke(scope, DOMENEPAKKE)

        brudd shouldHaveSize 1
        brudd.joinToString("\n") shouldContain "ligger i fixtures.domene.infra"
    }

    @Test
    fun `flagger infra-import både fra domenepakken selv og fra infra-underpakken`() {
        val brudd = DomenepakkeUtenInfrastruktur.bruddInfraImport(scope, DOMENEPAKKE)

        brudd shouldHaveSize 2
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "importerer eksempel.infrastruktur.Klient"
        samlet shouldContain "importerer eksempel.infra.Klient"
        samlet shouldNotContain "Ren.kt"
    }

    /** `fixtures.domeneannet` er en naboppakke, ikke en underpakke — den skal ikke dras med av prefikset. */
    @Test
    fun `en pakke som bare deler tegn med domenepakken er utenfor`() {
        val samlet = (
            DomenepakkeUtenInfrastruktur.bruddInfraUnderpakke(scope, DOMENEPAKKE) +
                DomenepakkeUtenInfrastruktur.bruddInfraImport(scope, DOMENEPAKKE)
            ).joinToString("\n")

        samlet shouldNotContain "Utenfor.kt"
    }

    /** Med `time` som infra-segment i tillegg blir også `kotlin.time.Duration` en infrastruktur-import. */
    @Test
    fun `ekstra infra-segmenter utvider standardsettet`() {
        val brudd = DomenepakkeUtenInfrastruktur.bruddInfraImport(scope, DOMENEPAKKE, ekstraInfraSegmenter = setOf("time"))

        brudd shouldHaveSize 3
        brudd.joinToString("\n") shouldContain "importerer kotlin.time.Duration"
    }

    @Test
    fun `unntatte filstier flagges ikke av importregelen`() {
        val brudd = DomenepakkeUtenInfrastruktur.bruddInfraImport(
            scope,
            DOMENEPAKKE,
            unntatteFilstier = setOf("domenepakke/Importbrudd.kt"),
        )

        brudd shouldHaveSize 1
        brudd.joinToString("\n") shouldNotContain "Importbrudd.kt"
    }

    @Test
    fun `en pakke uten treff gir ingen brudd`() {
        DomenepakkeUtenInfrastruktur.bruddInfraUnderpakke(scope, "fixtures.finnesikke").shouldBeEmpty()
        DomenepakkeUtenInfrastruktur.bruddInfraImport(scope, "fixtures.finnesikke").shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        shouldThrow<AssertionError> {
            DomenepakkeUtenInfrastruktur.assertIngenInfraUnderpakker(scope, DOMENEPAKKE)
        }.message shouldContain "Infrastruktur hører hjemme under `infra`"

        shouldThrow<AssertionError> {
            DomenepakkeUtenInfrastruktur.assertIngenInfraImport(scope, DOMENEPAKKE)
        }.message shouldContain "skal ikke avhenge av infrastruktur"
    }

    /** Uten vakten er begge reglene grønne på et feilstavet pakkenavn — som er nøyaktig det samme signalet som et tomt scope. */
    @Test
    fun `vakten slår ut når domenepakken ikke finnes`() {
        DomenepakkeUtenInfrastruktur.assertFinnerDomenepakken(scope, DOMENEPAKKE, minstAntallFiler = 3)

        shouldThrow<AssertionError> {
            DomenepakkeUtenInfrastruktur.assertFinnerDomenepakken(scope, "fixtures.domenee", minstAntallFiler = 3)
        }.message shouldContain "fant 0 filer i fixtures.domenee"
    }

    private companion object {
        const val DOMENEPAKKE = "fixtures.domene"
    }
}
