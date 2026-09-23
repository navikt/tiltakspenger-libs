package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class WireMockKunForWireFormatTest {
    private val scope = fixtureScope("wiremock")

    @Test
    fun `flagger wiremock-importer uavhengig av pakke og bokstavstørrelse`() {
        val wiremockBrudd = WireMockKunForWireFormat.brudd(scope)

        // Tre importer i Brudd.kt og én i BruddWhitelistet.kt.
        wiremockBrudd shouldHaveSize 4
        val samlet = wiremockBrudd.joinToString("\n")
        samlet shouldContain "com.github.tomakehurst.wiremock.WireMockServer"
        samlet shouldContain "com.marcinziolo.kotlin.wiremock.post"
        samlet shouldContain "no.nav.tiltakspenger.libs.common.withWireMockServer"
        samlet shouldNotContain "Ren.kt"
    }

    @Test
    fun `tillatte filer flagges ikke`() {
        val brudd = WireMockKunForWireFormat.brudd(scope, tillatteFiler = setOf("wiremock/BruddWhitelistet.kt"))

        brudd shouldHaveSize 3
        brudd.joinToString("\n") shouldNotContain "BruddWhitelistet.kt"
    }

    @Test
    fun `fake-baserte tester, kommentarer og strengliteraler flagges ikke`() {
        WireMockKunForWireFormat.brudd(scope).filter { brudd -> "Ren.kt" in brudd }.shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { WireMockKunForWireFormat.assert(scope) }
        feil.message shouldContain "FakeHttpTransport"
    }
}
