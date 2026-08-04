package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * Route-test-buildere uttrykker forventninger gjennom `forventet: ForventetRespons?` fra ktor-test-common, og bare det.
 * Én builder per endepunkt: feiltilfeller dekkes av forventet-parameteren, aldri av egne overloads, og assertions bor i libs-hjelperen `defaultRequestWithAssertions` eller i testen — aldri i builderen, der hvert kallsted ville arvet dem i stillhet.
 *
 * Regelen har tre lag over filene [builderFilPredikat] velger ut:
 * [forventetParametre] forbyr parametre navngitt `forventetStatus`/`forventetBody`/`forventetJsonBody` — flate parametre gjenskaper `ForventetRespons` på hvert kallsted, og en glemt gjennomkobling gir parametre som stille asserter ingenting.
 * [returnerResponsFunksjoner] forbyr funksjoner navngitt `*ReturnerRespons` — feiltilfelle-overloads av builderne.
 * [assertionsIBuildere] forbyr kotest-importer i builder-filer — en builder som asserter domenetilstand påfører alle kallere assertions de ikke ser.
 *
 * Standardpredikatet velger filer som slutter på `Builder.kt` og ligger under en `infra/route`-katalog; domene-buildere og testfiler er dermed utenfor.
 * Kalleren sender `scopeFromTest()` og kan overstyre predikatet eller unnta enkeltfiler via [unntatteFilstier].
 */
object RouteBuilderKontrakt {

    val standardBuilderFilPredikat: (KoFileDeclaration) -> Boolean =
        { file -> file.path.endsWith("Builder.kt") && "/infra/route" in file.path }

    fun forventetParametre(
        scope: KoScope,
        builderFilPredikat: (KoFileDeclaration) -> Boolean = standardBuilderFilPredikat,
        unntatteFilstier: Set<String> = emptySet(),
        ekstraForbudteForventetParametre: Set<String> = emptySet(),
    ): List<String> {
        val forbudteParametre = standardForbudteForventetParametre + ekstraForbudteForventetParametre
        return builderfiler(scope, builderFilPredikat, unntatteFilstier).flatMap { file ->
            file
                .functions(includeNested = true, includeLocal = true)
                .flatMap { funksjon -> funksjon.parameters }
                .filter { parameter -> parameter.name in forbudteParametre }
                .map { parameter -> "${parameter.location}: parameteren ${parameter.name} — uttrykk forventningen med forventet: ForventetRespons?" }
        }
    }

    fun returnerResponsFunksjoner(
        scope: KoScope,
        builderFilPredikat: (KoFileDeclaration) -> Boolean = standardBuilderFilPredikat,
        unntatteFilstier: Set<String> = emptySet(),
    ): List<String> = builderfiler(scope, builderFilPredikat, unntatteFilstier).flatMap { file ->
        file
            .functions(includeNested = true, includeLocal = true)
            .filter { funksjon -> funksjon.name.endsWith("ReturnerRespons") }
            .map { funksjon -> "${funksjon.location}: ${funksjon.name}" }
    }

    fun assertionsIBuildere(
        scope: KoScope,
        builderFilPredikat: (KoFileDeclaration) -> Boolean = standardBuilderFilPredikat,
        unntatteFilstier: Set<String> = emptySet(),
    ): List<String> = builderfiler(scope, builderFilPredikat, unntatteFilstier).flatMap { file ->
        file.imports
            .filter { import -> import.name.startsWith("io.kotest.") }
            .map { import -> "${file.path}: ${import.name}" }
    }

    fun assertForventetParametre(
        scope: KoScope,
        builderFilPredikat: (KoFileDeclaration) -> Boolean = standardBuilderFilPredikat,
        unntatteFilstier: Set<String> = emptySet(),
        ekstraForbudteForventetParametre: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        forventetParametre(scope, builderFilPredikat, unntatteFilstier, ekstraForbudteForventetParametre),
        "Route-buildere tar forventet: ForventetRespons? — ikke flate forventetStatus-/forventetBody-parametre som gjenskaper typen på hvert kallsted.",
    )

    fun assertIngenReturnerResponsFunksjoner(
        scope: KoScope,
        builderFilPredikat: (KoFileDeclaration) -> Boolean = standardBuilderFilPredikat,
        unntatteFilstier: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        returnerResponsFunksjoner(scope, builderFilPredikat, unntatteFilstier),
        "Én builder per endepunkt: feiltilfeller uttrykkes med forventet-parameteren, ikke med egne ReturnerRespons-overloads.",
    )

    fun assertIngenAssertionsIBuildere(
        scope: KoScope,
        builderFilPredikat: (KoFileDeclaration) -> Boolean = standardBuilderFilPredikat,
        unntatteFilstier: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        assertionsIBuildere(scope, builderFilPredikat, unntatteFilstier),
        "Route-buildere asserter ikke selv — forventninger går gjennom ForventetRespons, og øvrige assertions bor i testen.",
    )

    private fun builderfiler(
        scope: KoScope,
        builderFilPredikat: (KoFileDeclaration) -> Boolean,
        unntatteFilstier: Set<String>,
    ): List<KoFileDeclaration> = scope
        .kildefiler()
        .filter(builderFilPredikat)
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }

    /** Parameternavnene som skal uttrykkes med `forventet: ForventetRespons?`; et repo med flere egne varianter legger dem til. */
    val standardForbudteForventetParametre = setOf("forventetStatus", "forventetBody", "forventetJsonBody")
}
