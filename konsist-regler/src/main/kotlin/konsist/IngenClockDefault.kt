package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration

/**
 * `Clock`-parametre skal være påkrevd i produksjonskode — aldri ha default-verdi.
 * Systemklokka defineres kun ytterst, på composition root, og sendes eksplisitt nedover.
 * En default som `Clock.systemUTC()` skjuler kallsteder som glemmer å sende klokka, og gjør koden utestbar i det stille.
 * Se «Ingen standardverdier i domenetyper eller offentlige API-er» i tiltakspenger-libs/AGENTS.md.
 *
 * Deteksjonen bruker Konsists deklarasjons-API: alle konstruktør- og funksjonsparametre av type `Clock` med default-verdi flagges.
 * Properties (`val clock: Clock = ...`) flagges ikke — en tilordning på composition root er en definisjon, ikke en default.
 *
 * [unntatteFilstier] er sti-suffikser som unntas — typisk composition root-filer der en default er selve definisjonen (f.eks. en `start()`-funksjon i `Application.kt`).
 * Kalleren velger scope: typisk `scopeFromProduction()`, siden tester legitimt bruker `fixedClock`/`TikkendeKlokke` som default.
 */
object IngenClockDefault {

    fun brudd(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope.kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            val funksjonsparametre = file.functions(includeNested = true, includeLocal = true).flatMap { it.parameters }
            val konstruktørparametre = file.classes(includeNested = true).flatMap { klasse -> klasse.constructors.flatMap { it.parameters.orEmpty() } }
            (konstruktørparametre + funksjonsparametre)
                .filter { parameter -> parameter.erClockMedDefault() }
                .map { parameter -> "${parameter.location}: parameteren `${parameter.name}: Clock` har default-verdi `${parameter.defaultValue}`" }
        }

    fun assert(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, unntatteFilstier),
        "Clock-parametre skal være påkrevd — definer systemklokka kun på composition root (se «Ingen standardverdier» i tiltakspenger-libs/AGENTS.md).",
    )

    private fun KoParameterDeclaration.erClockMedDefault(): Boolean = type.name == "Clock" && hasDefaultValue()
}
