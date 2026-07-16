package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Kun infra-pakker kan importere infra-pakker.
 * En pakke regnes som infra når den har et pakkesegment i [standardInfraSegmenter] (eller settet kalleren sender inn).
 *
 * Scope-valget styrer hva regelen betyr: en enmodul-app kjører den på `scopeFromProduction()`, mens et monorepo med domene-/infrastruktur-splitt kjører den på et slice av domene-modulene (f.eks. `scopeFromProject().slice { "-domene/" in it.path }`).
 */
object InfraImport {

    val standardInfraSegmenter = setOf("infra")

    fun brudd(scope: KoScope, infraSegmenter: Set<String> = standardInfraSegmenter): List<String> = scope.kildefiler()
        .filterNot { file -> file.packagee?.name.orEmpty().harSegmentI(infraSegmenter) }
        .flatMap { file ->
            file.imports
                .filter { import -> import.name.harSegmentI(infraSegmenter) }
                .map { import -> "${file.path}: ${file.packagee?.name.orEmpty()} importerer ${import.name}" }
        }

    fun assert(scope: KoScope, infraSegmenter: Set<String> = standardInfraSegmenter) = assertIngenBrudd(
        brudd(scope, infraSegmenter),
        "Kun infra-pakker kan importere infra-pakker (segmenter: $infraSegmenter).",
    )
}

/** True når minst ett pakkesegment (punktum-separert) er i [segmenter]. */
internal fun String.harSegmentI(segmenter: Set<String>): Boolean = split('.').any { segment -> segment in segmenter }
