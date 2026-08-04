package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Kun infra-pakker kan importere infra-pakker.
 * En pakke regnes som infra når den har et pakkesegment i [standardInfraSegmenter], utvidet med det kalleren sender i `ekstraInfraSegmenter`.
 *
 * Scope-valget styrer hva regelen betyr: en enmodul-app kjører den på `scopeFromProduction()`, mens et monorepo med domene-/infrastruktur-splitt kjører den på et slice av domene-modulene (f.eks. `scopeFromProject().slice { "-domene/" in it.path }`).
 */
object InfraImport {

    /** Segmentet flåten bruker selv; repoer som i tillegg må gjenkjenne f.eks. `infrastruktur` legger det til i stedet for å erstatte settet. */
    val standardInfraSegmenter = setOf("infra")

    fun brudd(scope: KoScope, ekstraInfraSegmenter: Set<String> = emptySet()): List<String> {
        val infraSegmenter = standardInfraSegmenter + ekstraInfraSegmenter
        return scope.kildefiler()
            .filterNot { file -> file.packagee?.name.orEmpty().harSegmentI(infraSegmenter) }
            .flatMap { file ->
                file.imports
                    .filter { import -> import.name.harSegmentI(infraSegmenter) }
                    .map { import -> "${file.path}: ${file.packagee?.name.orEmpty()} importerer ${import.name}" }
            }
    }

    fun assert(scope: KoScope, ekstraInfraSegmenter: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, ekstraInfraSegmenter),
        "Kun infra-pakker kan importere infra-pakker (segmenter: ${standardInfraSegmenter + ekstraInfraSegmenter}).",
    )
}

/** True når minst ett pakkesegment (punktum-separert) er i [segmenter]. */
internal fun String.harSegmentI(segmenter: Set<String>): Boolean = split('.').any { segment -> segment in segmenter }

/**
 * True når pakkenavnet er [pakke] selv eller en underpakke av den.
 * Sammenligningen er segmentvis: `no.nav.x.felles` drar ikke med seg `no.nav.x.fellesskap`.
 */
internal fun String?.erLikEllerUnder(pakke: String): Boolean = this == pakke || this?.startsWith("$pakke.") == true
