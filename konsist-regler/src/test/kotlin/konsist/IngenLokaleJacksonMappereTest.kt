package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenLokaleJacksonMappereTest {
    private val scope = fixtureScope("mappere")

    @Test
    fun `flagger builder, jacksonObjectMapper og ktor-DSL, men ikke json-pakka, kommentarer eller ren bruk`() {
        val brudd = IngenLokaleJacksonMappere.brudd(scope)

        brudd shouldHaveSize 4
        brudd.count { it.contains("BruddBuilder.kt") } shouldBe 2
        brudd.count { it.contains("BruddKtorDsl.kt") } shouldBe 1
        brudd.count { it.contains("BruddWhitelistet.kt") } shouldBe 1
        brudd.single { it.contains("BruddKtorDsl.kt") } shouldContain "jackson3 {"
    }

    @Test
    fun `tillatteFiler unntar bevisste unntak med suffiks-match`() {
        val brudd = IngenLokaleJacksonMappere.brudd(scope, tillatteFiler = setOf("mappere/BruddWhitelistet.kt"))

        brudd shouldHaveSize 3
        brudd.count { it.contains("BruddWhitelistet.kt") } shouldBe 0
    }
}
