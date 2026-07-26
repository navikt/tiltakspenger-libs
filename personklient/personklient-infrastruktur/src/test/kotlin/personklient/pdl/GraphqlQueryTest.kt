package no.nav.tiltakspenger.libs.personklient.pdl

import io.kotest.assertions.json.shouldEqualJson
import no.nav.tiltakspenger.libs.json.serialize
import org.junit.jupiter.api.Test

/**
 * Spørringstypene er payloaden konsumentene sender til [FellesPersonklient.graphqlRequest], så testen låser JSON-formen.
 */
internal class GraphqlQueryTest {
    @Test
    fun `enkeltspørring serialiseres med query og variabler`() {
        val query = GraphqlQuery(
            query = "query(${'$'}ident: ID!) { hentPerson(ident: ${'$'}ident) { navn { fornavn } } }",
            variables = mapOf("ident" to "12345678910"),
        )

        serialize(query) shouldEqualJson """
            {
              "query": "query(${'$'}ident: ID!) { hentPerson(ident: ${'$'}ident) { navn { fornavn } } }",
              "variables": { "ident": "12345678910" }
            }
        """.trimIndent()
    }

    @Test
    fun `bolkspørring serialiseres med liste av identer`() {
        val query = GraphqlBolkQuery(
            query = "query(${'$'}identer: [ID!]!) { hentPersonBolk(identer: ${'$'}identer) { ident } }",
            variables = mapOf("identer" to listOf("12345678910", "10987654321")),
        )

        serialize(query) shouldEqualJson """
            {
              "query": "query(${'$'}identer: [ID!]!) { hentPersonBolk(identer: ${'$'}identer) { ident } }",
              "variables": { "identer": ["12345678910", "10987654321"] }
            }
        """.trimIndent()
    }
}
