package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Domene-filer importerer kun pakker fra en eksplisitt whitelist.
 * En import er tillatt når den ikke har et infra-segment og er lik eller under en av prefiksene i `tillattePakker`.
 *
 * Kalleren definerer hva som er domene: `erDomenepakke` får pakkenavnet og svarer.
 * Filer i pakker med infra-segment regnes aldri som domene, uansett hva predikatet svarer.
 * En enmodul-app sender typisk `{ it.startsWith("no.nav.<app>") }` på `scopeFromProduction()`; et monorepo slicer scope til domene-modulene og sender `{ true }`.
 */
object DomeneImportWhitelist {

    fun brudd(
        scope: KoScope,
        erDomenepakke: (String) -> Boolean,
        tillattePakker: Collection<String>,
        infraSegmenter: Set<String> = InfraImport.standardInfraSegmenter,
    ): List<String> = scope.kildefiler()
        .filter { file ->
            val pakke = file.packagee?.name ?: return@filter false
            !pakke.harSegmentI(infraSegmenter) && erDomenepakke(pakke)
        }
        .flatMap { file ->
            file.imports
                .filterNot { import -> import.name.erTillatt(tillattePakker, infraSegmenter) }
                .map { import -> "${file.path}: ${file.packagee?.name.orEmpty()} importerer ${import.name}" }
        }

    fun assert(
        scope: KoScope,
        erDomenepakke: (String) -> Boolean,
        tillattePakker: Collection<String>,
        infraSegmenter: Set<String> = InfraImport.standardInfraSegmenter,
    ) = assertIngenBrudd(
        brudd(scope, erDomenepakke, tillattePakker, infraSegmenter),
        "Domene-filer importerer kun pakker fra whitelisten:\n" + tillattePakker.joinToString("\n") { pakke -> "- $pakke.*" },
    )

    private fun String.erTillatt(tillattePakker: Collection<String>, infraSegmenter: Set<String>): Boolean =
        !harSegmentI(infraSegmenter) && tillattePakker.any { pakke -> this == pakke || startsWith("$pakke.") }
}
