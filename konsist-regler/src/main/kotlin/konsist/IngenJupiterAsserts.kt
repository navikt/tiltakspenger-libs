package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Vi bruker Kotest assertions.
 * Jupiter assertion-importer som `org.junit.jupiter.api.Assertions` og `org.junit.jupiter.api.assertThrows` skal ikke brukes.
 *
 * Kjøres typisk med `scopeFromTest()`.
 */
object IngenJupiterAsserts {

    /**
     * Importprefiksene som er Jupiter-assertions.
     * `...api.Assertions` dekker både klassen selv og statiske importer fra den, og `...api.assert` dekker toppnivåfunksjonene (`assertThrows`, `assertAll`).
     */
    val standardForbudtePrefikser = setOf("org.junit.jupiter.api.Assertions", "org.junit.jupiter.api.assert")

    fun brudd(scope: KoScope, ekstraForbudtePrefikser: Set<String> = emptySet()): List<String> {
        val forbudte = standardForbudtePrefikser + ekstraForbudtePrefikser
        return scope.kildefiler().flatMap { file ->
            file.imports
                .filter { import -> forbudte.any { prefiks -> import.name.startsWith(prefiks) } }
                .map { import -> "${file.path}: ${import.name}" }
        }
    }

    fun assert(scope: KoScope, ekstraForbudtePrefikser: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, ekstraForbudtePrefikser),
        "Bruk Kotest assertions (io.kotest.matchers.* / io.kotest.assertions.*). Følgende Jupiter Assertions-importer er ikke tillatt.",
    )
}
