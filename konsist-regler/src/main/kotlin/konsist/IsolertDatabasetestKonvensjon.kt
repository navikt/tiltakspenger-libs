package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Tester som ber om isolert database med `runIsolated = true` skal følge konvensjonen som gjør isolasjonen trygg og sporbar under parallellkjøring.
 * Isolert modus tømmer databasen og serialiserer testen, så den er reservert for aggregerte spørringer på tvers av saker — og da må runneren vite om det, og leseren må vite hvorfor.
 *
 * Regelen har to lag:
 * [runIsolatedUtenAnnotasjon] krever at hvert brukssted har `@`[annotasjonsnavn] mellom nærmeste test-annotasjon og bruksstedet, slik at runneren serialiserer testen via annotasjonens `@ResourceLock`.
 * [runIsolatedUtenBegrunnelse] (opt-in) krever i tillegg en begrunnelseslinje i samme område — enten den faste forklaringen eller en exit-plan-TODO, se [standardBegrunnelser].
 *
 * Bruksstedene finnes via [no.nav.tiltakspenger.libs.konsist.kodelinjer], så omtale i kommentarer og strengliteraler teller ikke.
 * Området som sjekkes er linjene fra nærmeste test-annotasjon til bruksstedet, pluss sammenhengende kommentarlinjer rett over annotasjonen.
 * Et brukssted uten test-annotasjon over seg rapporteres som utenfor en test — isolasjon skal ikke gjemmes i hjelpefunksjoner.
 *
 * Kalleren sender `scopeFromTest()`; filene som definerer hjelperne unntas via [unntatteFilstier] om nødvendig.
 */
object IsolertDatabasetestKonvensjon {

    /** Begrunnelsene flåten godtar; et repo med en egen kategori isolasjonsgjeld legger den til med `ekstraBegrunnelser`. */
    val standardBegrunnelser = listOf(
        "Aggregert spørring på tvers av saker",
        "TODO: Kan flippes til runIsolated = false",
    )

    fun runIsolatedUtenAnnotasjon(
        scope: KoScope,
        annotasjonsnavn: String = "IsolatedDatabaseTest",
        unntatteFilstier: Set<String> = emptySet(),
    ): List<String> = bruksteder(scope, unntatteFilstier).mapNotNull { brukssted ->
        when {
            brukssted.testAnnotasjonIndeks == null ->
                "${brukssted.sti}:${brukssted.linjenummer}: runIsolated = true utenfor en test — isolasjon skal stå i testen, ikke i en hjelpefunksjon"

            !brukssted.harIOmråde("@$annotasjonsnavn") ->
                "${brukssted.sti}:${brukssted.linjenummer}: runIsolated = true uten @$annotasjonsnavn på testen"

            else -> null
        }
    }

    fun runIsolatedUtenBegrunnelse(
        scope: KoScope,
        ekstraBegrunnelser: List<String> = emptyList(),
        unntatteFilstier: Set<String> = emptySet(),
    ): List<String> {
        val begrunnelser = standardBegrunnelser + ekstraBegrunnelser
        return bruksteder(scope, unntatteFilstier).mapNotNull { brukssted ->
            if (begrunnelser.any { begrunnelse -> brukssted.harIOmråde(begrunnelse) }) {
                null
            } else {
                "${brukssted.sti}:${brukssted.linjenummer}: runIsolated = true uten begrunnelse — forventet en linje med en av: ${begrunnelser.joinToString(" | ")}"
            }
        }
    }

    fun assertRunIsolatedHarAnnotasjon(
        scope: KoScope,
        annotasjonsnavn: String = "IsolatedDatabaseTest",
        unntatteFilstier: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        runIsolatedUtenAnnotasjon(scope, annotasjonsnavn, unntatteFilstier),
        "Tester med runIsolated = true skal være annotert slik at runneren serialiserer dem under parallellkjøring.",
    )

    fun assertRunIsolatedHarBegrunnelse(
        scope: KoScope,
        ekstraBegrunnelser: List<String> = emptyList(),
        unntatteFilstier: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        runIsolatedUtenBegrunnelse(scope, ekstraBegrunnelser, unntatteFilstier),
        "Tester med runIsolated = true skal begrunne isolasjonen eller ha en exit-plan-TODO, slik at gjelden er synlig der den bor.",
    )

    private class Brukssted(
        val sti: String,
        val linjenummer: Int,
        val testAnnotasjonIndeks: Int?,
        private val linjer: List<String>,
    ) {
        /** Området fra test-annotasjonen (med kommentarblokka rett over) til og med bruksstedet; uten test-annotasjon kun bruksstedets linje. */
        fun harIOmråde(tekst: String): Boolean {
            val start = testAnnotasjonIndeks?.let { indeks -> utvidMedKommentarblokk(indeks) } ?: (linjenummer - 1)
            return (start until linjenummer).any { indeks -> tekst in linjer[indeks] }
        }

        private fun utvidMedKommentarblokk(annotasjonIndeks: Int): Int {
            var indeks = annotasjonIndeks
            while (indeks > 0 && linjer[indeks - 1].trim().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }) {
                indeks--
            }
            return indeks
        }
    }

    private fun bruksteder(scope: KoScope, unntatteFilstier: Set<String>): List<Brukssted> = scope
        .kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            val linjer = file.text.lines()
            file
                .kodelinjer()
                .filter { (_, kode) -> "runIsolated = true" in kode }
                .map { (linjenummer, _) ->
                    Brukssted(
                        sti = file.path,
                        linjenummer = linjenummer,
                        testAnnotasjonIndeks = (linjenummer - 2 downTo 0).firstOrNull { indeks -> linjer[indeks].trim() in testannotasjoner },
                        linjer = linjer,
                    )
                }
        }

    private val testannotasjoner = setOf("@Test", "@ParameterizedTest", "@RepeatedTest", "@TestFactory", "@TestTemplate")
}
