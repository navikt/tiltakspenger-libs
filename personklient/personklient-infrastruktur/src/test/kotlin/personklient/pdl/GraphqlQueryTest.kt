package no.nav.tiltakspenger.libs.personklient.pdl

import io.kotest.assertions.json.shouldEqualJson
import no.nav.tiltakspenger.libs.common.FnrGenerator
import no.nav.tiltakspenger.libs.json.serialize
import org.junit.jupiter.api.Test

/**
 * Spørringstypene er payloaden konsumentene sender til [FellesPersonklient.graphqlRequest], så testen låser JSON-formen.
 */
internal class GraphqlQueryTest {
    @Test
    fun `enkeltspørring serialiseres med query og variabler`() {
        val fnr = FnrGenerator().generer().verdi
        val query = GraphqlQuery(
            query = "query(${'$'}ident: ID!) { hentPerson(ident: ${'$'}ident) { navn { fornavn } } }",
            variables = mapOf("ident" to fnr),
        )

        serialize(query) shouldEqualJson """
            {
              "query": "query(${'$'}ident: ID!) { hentPerson(ident: ${'$'}ident) { navn { fornavn } } }",
              "variables": { "ident": "$fnr" }
            }
        """.trimIndent()
    }

    @Test
    fun `bolkspørring serialiseres med liste av identer`() {
        val generator = FnrGenerator()
        val fnr = generator.generer().verdi
        val historiskFnr = generator.generer().verdi
        val query = GraphqlBolkQuery(
            query = "query(${'$'}identer: [ID!]!) { hentPersonBolk(identer: ${'$'}identer) { ident } }",
            variables = mapOf("identer" to listOf(fnr, historiskFnr)),
        )

        serialize(query) shouldEqualJson """
            {
              "query": "query(${'$'}identer: [ID!]!) { hentPersonBolk(identer: ${'$'}identer) { ident } }",
              "variables": { "identer": ["$fnr", "$historiskFnr"] }
            }
        """.trimIndent()
    }
}
