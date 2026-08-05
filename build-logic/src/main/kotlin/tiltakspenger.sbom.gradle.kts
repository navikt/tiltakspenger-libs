import org.cyclonedx.gradle.BaseCyclonedxTask
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component

/**
 * Genererer en samlet CycloneDX-SBOM for det repoet publiserer, i `build/reports/cyclonedx/bom.json`.
 *
 * SBOM-en bygges fra avhengighetsgrafen, ikke ved å skanne jar-ene.
 * En libs-modul er en tynn jar der nesten alt innholdet ligger i de transitive avhengighetene, så en skanner som bare ser jar-innholdet ville rapportert nesten ingenting.
 *
 * Pluginen hører hjemme på rotprosjektet, som aggregerer submodulene.
 * Tasken henges bevisst ikke på `build`: den er en leveranse fra publiseringsworkflowen, og skal ikke koste tid i hvert lokale bygg.
 */

plugins {
    id("org.cyclonedx.bom")
}

// Rotprosjektet publiseres ikke og har derfor ingen group fra `tiltakspenger.publisering`.
// Uten den blir rotnoden i komponentgrafen liggende på `pkg:maven/unspecified/...`, med en annen bom-ref enn `metadata.component` —
// og en konsument som følger metadata-refen inn i `dependencies` finner ingenting.
group = "com.github.navikt.tiltakspenger-libs"

// Aggregattasken samler fra modulenes `cyclonedxDirectBom`, og det er der kildekonfigurasjonene velges.
// Uten avgrensningen beskriver SBOM-en også modulenes testavhengigheter, som ikke følger med konsumentene.
// Skillet finnes i CycloneDX kun som en property (`cdx:maven:package:test`), og de fleste skannere filtrerer ikke på den —
// resultatet ville vært CVE-treff på WireMock, Jetty og jackson 2 som aldri havner i en app.
// Testhjelpemodulene (test-common m.fl.) eksponerer WireMock på sin egen `runtimeClasspath` og beholder den, som seg hør og bør: der følger den faktisk med.
allprojects {
    tasks.withType<CyclonedxDirectTask>().configureEach {
        includeConfigs = listOf("runtimeClasspath")
    }
}

tasks.named<BaseCyclonedxTask>("cyclonedxBom") {
    componentGroup = "com.github.navikt.tiltakspenger-libs"
    projectType = Component.Type.LIBRARY
}
