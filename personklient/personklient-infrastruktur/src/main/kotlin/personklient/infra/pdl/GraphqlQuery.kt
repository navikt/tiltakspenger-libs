package no.nav.tiltakspenger.libs.personklient.infra.pdl

data class GraphqlQuery(
    val query: String,
    val variables: Map<String, String>,
)
