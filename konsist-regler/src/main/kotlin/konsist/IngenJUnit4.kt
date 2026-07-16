package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Vi bruker JUnit 5 (Jupiter).
 * JUnit 4 (`junit.framework.*` og `org.junit.*` utenom `org.junit.jupiter.*` / `org.junit.platform.*`) skal ikke brukes i testkode.
 *
 * Kjøres typisk med `scopeFromTest()`.
 */
object IngenJUnit4 {

    fun brudd(scope: KoScope): List<String> = scope.kildefiler().flatMap { file ->
        file.imports
            .filter { import ->
                import.name.startsWith("junit.framework.") ||
                    (
                        import.name.startsWith("org.junit.") &&
                            !import.name.startsWith("org.junit.jupiter.") &&
                            !import.name.startsWith("org.junit.platform.")
                        )
            }
            .map { import -> "${file.path}: ${import.name}" }
    }

    fun assert(scope: KoScope) = assertIngenBrudd(
        brudd(scope),
        "Bruk JUnit 5 (org.junit.jupiter.*). Følgende JUnit 4-importer er ikke tillatt.",
    )
}
