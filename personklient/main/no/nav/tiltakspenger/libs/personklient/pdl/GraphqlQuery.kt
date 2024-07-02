package no.nav.tiltakspenger.libs.personklient.pdl

internal data class GraphqlQuery(
    val query: String,
    val variables: Map<String, String>,
)
