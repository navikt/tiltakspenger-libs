package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Klienttester kjører produksjonsklienten over `FakeHttpTransport`; WireMock er kun tillatt i filer som bevisst verifiserer bytene på tråden (multipart-framing, binære svar).
 * Faken øver hele klient-pipelinen (auth, retry, statusregler, serialisering) på noen få millisekunder per test, mens en WireMock-test koster to størrelsesordener mer — mønsteret er derfor maks én wire-format-test per klient.
 *
 * Deteksjonen er import-basert på alt som inneholder «wiremock» uavhengig av store og små bokstaver.
 * Det dekker `org.wiremock`, `com.github.tomakehurst.wiremock`, `com.marcinziolo.kotlin.wiremock`, kotest-extensions og test-common-hjelpere som `withWireMockServer`.
 * Omtale i kommentarer og strenger teller ikke, siden bare importene leses.
 * Bevisst akseptert hull: bruk fra samme pakke som hjelperne trenger ingen import og fanges ikke.
 *
 * Kalleren sender `scopeFromTest()` og whitelister de bevisste wire-format-testene via [tillatteFiler] (sti-suffikser), med en kommentar om hvorfor på aktiveringsstedet.
 */
object WireMockKunForWireFormat {

    fun brudd(scope: KoScope, tillatteFiler: Set<String> = emptySet()): List<String> = scope
        .kildefiler()
        .filterNot { file -> tillatteFiler.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            file.imports
                .filter { import -> import.name.contains("wiremock", ignoreCase = true) }
                .map { import -> "${file.path}: ${import.name}" }
        }

    fun assert(scope: KoScope, tillatteFiler: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, tillatteFiler),
        "Klienttester kjører produksjonsklienten over FakeHttpTransport. WireMock er kun tillatt i whitelistede wire-format-tester som verifiserer bytene på tråden.",
    )
}
