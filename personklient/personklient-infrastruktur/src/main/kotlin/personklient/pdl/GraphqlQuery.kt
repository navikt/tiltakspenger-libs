package no.nav.tiltakspenger.libs.personklient.pdl

data class GraphqlQuery(
    val query: String,
    val variables: Map<String, String>,
)
