package no.nav.tiltakspenger.libs.persistering.infrastruktur

import kotliquery.Query
import org.intellij.lang.annotations.Language

@Suppress("unused")
fun sqlQuery(
    @Language("PostgreSQL")
    query: String,
    vararg params: Pair<String, Any?>,
): Query {
    return Query(query.trimIndent(), paramMap = mapOf(*params))
}
