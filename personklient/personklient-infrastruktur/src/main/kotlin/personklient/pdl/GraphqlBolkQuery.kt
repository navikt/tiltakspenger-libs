package no.nav.tiltakspenger.libs.personklient.pdl

data class GraphqlBolkQuery(
    val query: String,
    val variables: Map<String, List<String>>,
)
