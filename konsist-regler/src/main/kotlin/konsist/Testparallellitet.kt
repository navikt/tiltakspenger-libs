package no.nav.tiltakspenger.libs.konsist

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Parallellkjøringen av tester skal være slått på og likt konfigurert i `build.gradle.kts` og `src/test/resources/junit-platform.properties`.
 * Parallellkjøring er selve håndhevelsen av at tester ikke deler tilstand — en stille flipp tilbake til `same_thread` fjerner hele vernet uten at én test feiler.
 * De to kildene må være i sync fordi Gradle leser system-properties fra byggfila mens IDE-en leser properties-fila; er de ulike, oppfører testene seg forskjellig i de to kjøremiljøene.
 *
 * [standardInnstillinger] låser de tre parallell-nøklene; kalleren kan sende sitt eget kart og for eksempel også låse `junit.jupiter.testinstance.lifecycle.default`.
 * Byggfila sjekkes mot `systemProperty("<nøkkel>", "<verdi>")`-formen, som er den hele flåten bruker; kommentarlinjer teller ikke.
 * Regelen tar rota til én Gradle-modul; flermodul-repoer kaller den per modul som har tester.
 *
 * Kalleren sender modulrota som `Path`, typisk testens arbeidskatalog.
 */
object Testparallellitet {

    /** Innstillingene flåten krever; et repo som pinner flere junit-nøkler legger dem til med `ekstraInnstillinger`. */
    val standardInnstillinger = mapOf(
        "junit.jupiter.execution.parallel.enabled" to "true",
        "junit.jupiter.execution.parallel.mode.default" to "concurrent",
        "junit.jupiter.execution.parallel.mode.classes.default" to "concurrent",
    )

    /**
     * [ekstraInnstillinger] legges til [standardInnstillinger].
     * En nøkkel som allerede står i standardsettet avvises: da ville et repo kunnet skru av parallellkjøringen ved å «legge til» `enabled = false`, og regelen ville håndhevet sin egen avskrudde tilstand.
     */
    fun brudd(rot: Path, ekstraInnstillinger: Map<String, String> = emptyMap()): List<String> {
        val forventedeInnstillinger = standardInnstillinger + ekstraInnstillinger.utenKollisjoner()
        return propertiesbrudd(rot, forventedeInnstillinger) + byggfilbrudd(rot, forventedeInnstillinger)
    }

    fun assert(rot: Path, ekstraInnstillinger: Map<String, String> = emptyMap()) = assertIngenBrudd(
        brudd(rot, ekstraInnstillinger),
        "Parallellkjøring av tester er håndhevelsen av at tester ikke deler tilstand, og skal være likt konfigurert i build.gradle.kts og junit-platform.properties.",
    )

    private fun Map<String, String>.utenKollisjoner(): Map<String, String> {
        val kolliderende = keys.filter { nøkkel -> nøkkel in standardInnstillinger }
        require(kolliderende.isEmpty()) {
            "Kan ikke overstyre standardinnstillingene ${kolliderende.joinToString()} — ekstraInnstillinger legger til, den erstatter ikke."
        }
        return this
    }

    private fun propertiesbrudd(rot: Path, innstillinger: Map<String, String>): List<String> {
        val fil = rot.resolve(propertiesSti)
        if (!fil.exists()) {
            return listOf("$propertiesSti: fila mangler — uten den kjører IDE-en testene med andre innstillinger enn Gradle")
        }
        val verdier = fil
            .readLines()
            .map { linje -> linje.trim() }
            .filterNot { linje -> linje.isEmpty() || linje.startsWith("#") || linje.startsWith("!") }
            .mapNotNull { linje -> linje.split("=", limit = 2).takeIf { deler -> deler.size == 2 } }
            .associate { deler -> deler[0].trim() to deler[1].trim() }
        return innstillinger.mapNotNull { (nøkkel, forventet) ->
            when (val faktisk = verdier[nøkkel]) {
                null -> "$propertiesSti: mangler $nøkkel=$forventet"
                forventet -> null
                else -> "$propertiesSti: $nøkkel=$faktisk, forventet $forventet"
            }
        }
    }

    private fun byggfilbrudd(rot: Path, innstillinger: Map<String, String>): List<String> {
        val fil = rot.resolve("build.gradle.kts")
        if (!fil.exists()) {
            return listOf("build.gradle.kts: fila mangler under rota $rot")
        }
        val kode = fil
            .readLines()
            .filterNot { linje -> linje.trim().startsWith("//") }
            .joinToString("\n")
        return innstillinger.mapNotNull { (nøkkel, forventet) ->
            val regex = Regex("""systemProperty\s*\(\s*"${Regex.escape(nøkkel)}"\s*,\s*"([^"]*)"\s*\)""")
            when (val faktisk = regex.find(kode)?.groupValues?.get(1)) {
                null -> "build.gradle.kts: mangler systemProperty(\"$nøkkel\", \"$forventet\")"
                forventet -> null
                else -> "build.gradle.kts: systemProperty $nøkkel=$faktisk, forventet $forventet"
            }
        }
    }

    private val propertiesSti = "src/test/resources/junit-platform.properties"
}
