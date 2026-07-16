package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class InfraImportTest {
    @Test
    fun `flagger infra-import fra ikke-infra-pakke, men ikke fra infra-pakke`() {
        val brudd = InfraImport.brudd(fixtureScope("infraimport"))

        brudd shouldHaveSize 1
        brudd.single() shouldContain "fixtures.domene.noe importerer fixtures.infra.EnKlient"
    }
}
