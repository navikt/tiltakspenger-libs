package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * JUnit-livssyklusannotasjonene `@BeforeEach`, `@AfterEach`, `@BeforeAll`, `@AfterAll` og `@TestInstance` er forbudt i testkode.
 * Livssyklusmetoder finnes for å administrere delt tilstand, og delt tilstand er en race når testmetoder kjører parallelt med `per_class`-livssyklus (én instans deles av alle metodene).
 * `@TestInstance` overstyrer den globale livssyklusen fra junit-platform.properties per klasse og skjuler samme problem — livssyklus settes globalt, aldri per klasse.
 * Alternativet er å bygge hele konteksten inne i hver test, typisk via en kontekst-klasse som instansieres per test.
 *
 * Deteksjonen har to lag: importer av annotasjonene, og fullkvalifisert bruk funnet via [no.nav.tiltakspenger.libs.konsist.kodelinjer].
 * Bevisst akseptert hull: kotest sine livssyklus-hooks dekkes ikke, siden flåten kjører JUnit 5 med kotest kun som assertion-bibliotek.
 *
 * Kalleren sender `scopeFromTest()`.
 */
object IngenJUnitLivssyklus {

    fun brudd(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope
        .kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            val importbrudd = file.imports
                .filter { import -> import.name in livssyklusAnnotasjoner }
                .map { import -> "${file.path}: ${import.name}" }
            val tekstbrudd = file
                .kodelinjer()
                .filterNot { (_, kode) -> kode.trimStart().startsWith("import ") }
                .mapNotNull { (linjenummer, kode) ->
                    livssyklusAnnotasjoner
                        .firstOrNull { annotasjon -> annotasjon in kode }
                        ?.let { annotasjon -> "${file.path}:$linjenummer: $annotasjon" }
                }
            importbrudd + tekstbrudd
        }

    fun assert(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, unntatteFilstier),
        "Ingen JUnit-livssyklus i tester — @BeforeEach/@BeforeAll administrerer delt tilstand som blir en race under parallellkjøring, og @TestInstance skal settes globalt i junit-platform.properties, ikke per klasse. Bygg konteksten inne i hver test.",
    )

    private val livssyklusAnnotasjoner = setOf(
        "org.junit.jupiter.api.BeforeEach",
        "org.junit.jupiter.api.AfterEach",
        "org.junit.jupiter.api.BeforeAll",
        "org.junit.jupiter.api.AfterAll",
        "org.junit.jupiter.api.TestInstance",
    )
}
