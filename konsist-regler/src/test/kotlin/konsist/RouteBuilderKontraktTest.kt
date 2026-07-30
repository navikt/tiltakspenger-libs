package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class RouteBuilderKontraktTest {
    private val scope = fixtureScope("routebuilderkontrakt")

    @Test
    fun `flagger flate forventet-parametre i buildere`() {
        val brudd = RouteBuilderKontrakt.forventetParametre(scope)

        brudd shouldHaveSize 2
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "forventetStatus"
        samlet shouldContain "forventetBody"
        samlet shouldNotContain "RenBuilder.kt"
    }

    @Test
    fun `flagger ReturnerRespons-overloads i buildere`() {
        val brudd = RouteBuilderKontrakt.returnerResponsFunksjoner(scope)

        brudd shouldHaveSize 1
        brudd.first() shouldContain "taBehandlingReturnerRespons"
    }

    @Test
    fun `flagger kotest-importer i buildere`() {
        val brudd = RouteBuilderKontrakt.assertionsIBuildere(scope)

        brudd shouldHaveSize 1
        brudd.first() shouldContain "io.kotest.matchers.shouldBe"
        brudd.first() shouldContain "BruddBuilder.kt"
    }

    @Test
    fun `testfiler og buildere utenfor route-katalogene er utenfor kontrakten`() {
        val samlet = (
            RouteBuilderKontrakt.forventetParametre(scope) +
                RouteBuilderKontrakt.returnerResponsFunksjoner(scope) +
                RouteBuilderKontrakt.assertionsIBuildere(scope)
            ).joinToString("\n")

        samlet shouldNotContain "RenRouteTest.kt"
        samlet shouldNotContain "RenDomeneBuilder.kt"
    }

    @Test
    fun `predikatet kan overstyres`() {
        val brudd = RouteBuilderKontrakt.returnerResponsFunksjoner(
            scope,
            builderFilPredikat = { file -> file.path.endsWith("Builder.kt") },
        )

        // Med et videre predikat fanges også domene-builderen.
        brudd shouldHaveSize 2
        brudd.joinToString("\n") shouldContain "byggDomeneobjektReturnerRespons"
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        RouteBuilderKontrakt
            .forventetParametre(scope, unntatteFilstier = setOf("infra/route/BruddBuilder.kt"))
            .shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val parameterfeil = shouldThrow<AssertionError> { RouteBuilderKontrakt.assertForventetParametre(scope) }
        parameterfeil.message shouldContain "forventet: ForventetRespons?"

        val overloadfeil = shouldThrow<AssertionError> { RouteBuilderKontrakt.assertIngenReturnerResponsFunksjoner(scope) }
        overloadfeil.message shouldContain "Én builder per endepunkt"

        val assertionfeil = shouldThrow<AssertionError> { RouteBuilderKontrakt.assertIngenAssertionsIBuildere(scope) }
        assertionfeil.message shouldContain "Route-buildere asserter ikke selv"
    }
}
