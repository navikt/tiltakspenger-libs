package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Boundary-klasser (navn som slutter på DTO, Dto, Request, Response eller DbJson) skal ligge i infra-pakker.
 * Kjøres typisk på `scopeFromProduction()`.
 *
 * Regelen passer for applikasjoner med domene-/infra-lagdeling i pakkestrukturen.
 * Den passer ikke for bibliotek-repoer som bevisst publiserer kontraktstyper i egne `*-dtos`-moduler (som tiltakspenger-libs) — der er DTO-modulene selve leveransen, ikke et lag som skal gjemmes bak infra.
 */
object BoundaryKlasser {

    private val boundaryNavnRegex = Regex("""\b(?:data\s+)?(?:class|interface|enum\s+class)\s+\w*(?:DTO|Dto|Request|Response|DbJson)\b""")

    fun brudd(
        scope: KoScope,
        infraSegmenter: Set<String> = InfraImport.standardInfraSegmenter,
        tillatteFiler: Set<String> = emptySet(),
    ): List<String> = scope.kildefiler()
        .filterNot { file -> file.packagee?.name.orEmpty().harSegmentI(infraSegmenter) }
        .filterNot { file -> tillatteFiler.any { tillatt -> file.path.endsWith(tillatt) } }
        .flatMap { file ->
            boundaryNavnRegex.findAll(file.text).map { funn -> "${file.path}: ${funn.value}" }
        }

    fun assert(
        scope: KoScope,
        infraSegmenter: Set<String> = InfraImport.standardInfraSegmenter,
        tillatteFiler: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        brudd(scope, infraSegmenter, tillatteFiler),
        "Boundary-klasser (DTO/Dto/Request/Response/DbJson) skal ligge i infra-pakker.",
    )
}
