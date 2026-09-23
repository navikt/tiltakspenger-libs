package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class DomeneImportWhitelistTest {
    @Test
    fun `flagger importer utenfor whitelisten og infra-importer, men ikke tillatte eller ikke-domene`() {
        val brudd = DomeneImportWhitelist.brudd(
            scope = fixtureScope("domeneimport"),
            erDomenepakke = { pakke -> pakke.startsWith("fixtures.domene") },
            tillattePakker = listOf("arrow.core", "kotlin"),
        )

        brudd shouldHaveSize 2
        brudd[0] shouldContain "com.eksempel.eksternt.Klient"
        brudd[1] shouldContain "fixtures.infra.EnKlient"
    }
}
