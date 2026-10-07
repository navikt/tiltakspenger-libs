package no.nav.tiltakspenger.libs.personklient.infra.pdl

data class GraphqlBolkQuery(
    val query: String,
    val variables: Map<String, List<String>>,
)
