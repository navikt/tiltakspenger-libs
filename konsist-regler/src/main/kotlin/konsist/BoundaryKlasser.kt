package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Boundary-klasser (navn som slutter på et av suffiksene i [standardBoundarySuffikser]) skal ligge i infra-pakker.
 * Kjøres typisk på `scopeFromProduction()`.
 *
 * Regelen passer for applikasjoner med domene-/infra-lagdeling i pakkestrukturen.
 * Den passer ikke for bibliotek-repoer som bevisst publiserer kontraktstyper i egne `*-dtos`-moduler (som tiltakspenger-libs) — der er DTO-modulene selve leveransen, ikke et lag som skal gjemmes bak infra.
 *
 * Begge listene utvides, ikke erstattes: `ekstraInfraSegmenter` legges til [InfraImport.standardInfraSegmenter], og `ekstraBoundarySuffikser` til [standardBoundarySuffikser].
 */
object BoundaryKlasser {

    /** Navnesuffiksene som gjør en type til en boundary-type; et repo med egne navnekonvensjoner (f.eks. `Kommando`) legger dem til. */
    val standardBoundarySuffikser = setOf("DTO", "Dto", "Request", "Response", "DbJson")

    fun brudd(
        scope: KoScope,
        ekstraInfraSegmenter: Set<String> = emptySet(),
        tillatteFiler: Set<String> = emptySet(),
        ekstraBoundarySuffikser: Set<String> = emptySet(),
    ): List<String> {
        val infraSegmenter = InfraImport.standardInfraSegmenter + ekstraInfraSegmenter
        val navnRegex = boundaryNavnRegex(standardBoundarySuffikser + ekstraBoundarySuffikser)
        return scope.kildefiler()
            .filterNot { file -> file.packagee?.name.orEmpty().harSegmentI(infraSegmenter) }
            .filterNot { file -> tillatteFiler.any { tillatt -> file.path.endsWith(tillatt) } }
            .flatMap { file ->
                navnRegex.findAll(file.text).map { funn -> "${file.path}: ${funn.value}" }
            }
    }

    fun assert(
        scope: KoScope,
        ekstraInfraSegmenter: Set<String> = emptySet(),
        tillatteFiler: Set<String> = emptySet(),
        ekstraBoundarySuffikser: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        brudd(scope, ekstraInfraSegmenter, tillatteFiler, ekstraBoundarySuffikser),
        "Boundary-klasser (${(standardBoundarySuffikser + ekstraBoundarySuffikser).joinToString("/")}) skal ligge i infra-pakker.",
    )

    /** Regexen bygges av suffikssettet, slik at et tillegg fra kalleren treffer på lik linje med de standard. */
    private fun boundaryNavnRegex(suffikser: Set<String>) =
        Regex("""\b(?:data\s+)?(?:class|interface|enum\s+class)\s+\w*(?:${suffikser.joinToString("|") { suffiks -> Regex.escape(suffiks) }})\b""")
}
