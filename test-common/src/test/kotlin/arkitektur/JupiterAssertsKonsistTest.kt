package arkitektur

import com.lemonappdev.konsist.api.Konsist
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test

/**
 * Vi bruker Kotest assertions i hele tiltakspenger-libs. Jupiter assertion-importer som
 * `org.junit.jupiter.api.Assertions` og `org.junit.jupiter.api.assertThrows` skal ikke brukes.
 *
 * Konsist `scopeFromTest()` skanner alle moduler, så denne ene testen dekker hele repoet.
 */
class JupiterAssertsKonsistTest {
    @Test
    fun `all testkode bruker Kotest assertions, ikke Jupiter Assertions`() {
        val violations =
            Konsist
                .scopeFromTest()
                .files
                .flatMap { file ->
                    file.imports
                        .filter { import ->
                            import.name == "org.junit.jupiter.api.Assertions" ||
                                import.name.startsWith("org.junit.jupiter.api.Assertions.") ||
                                import.name.startsWith("org.junit.jupiter.api.assert")
                        }
                        .map { import -> "${file.path}: ${import.name}" }
                }

        withClue(
            "Bruk Kotest assertions. Følgende Jupiter assertion-importer er ikke tillat:\n" +
                violations.joinToString("\n"),
        ) {
            violations.shouldBeEmpty()
        }
    }
}
