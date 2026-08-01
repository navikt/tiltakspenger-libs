package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider

/**
 * `internal` hører hjemme i tiltakspenger-libs, ikke i applikasjonsrepoene.
 *
 * Modifikatoren avgrenser synligheten til kompileringsmodulen — ikke til pakken, fila eller laget.
 * Applikasjonsrepoene er enmodul-repoer som ikke publiseres, og testkildesettet er assosiert med hovedkildesettet, så testene ser `internal`-erklæringene uansett.
 * Der avgrenser modifikatoren derfor ingenting `public` ikke allerede gjør: den ser ut som en tilgangsgrense uten å være en, og leser man den som en, er innkapslingen innbilt.
 * Skal noe faktisk skjermes, er `private` (fil- eller klassenivå) den eneste grensen et enmodul-repo har.
 *
 * I libs er `internal` derimot bærende: modulene publiseres som artefakter, og modifikatoren er det som holder en type utenfor det publiserte API-et.
 * Regelen er derfor laget for å kalles fra de andre repoene, og kjøres ikke i libs selv.
 *
 * Dekker alle erklæringer som kan bære synlighet: klasser (også enum- og data-klasser), interfaces, objects, funksjoner, properties, typealiaser, konstruktører og `internal set`.
 * Kalleren sender typisk `scopeFromProject()` — argumentet gjelder likt for produksjons- og testkode.
 */
object IngenInternalModifier {

    fun brudd(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope
        .kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            val modifikatorlinjer = file.modifikatorlinjer()
            file.navngitteBrudd(modifikatorlinjer) + file.konstruktørOgSetterbrudd(modifikatorlinjer)
        }

    fun assert(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, unntatteFilstier),
        "internal avgrenser til kompileringsmodulen, og et enmodul-repo som ikke publiseres har ingen slik grense å avgrense mot — kun tiltakspenger-libs har det. Fjern modifikatoren, eller bruk private hvis erklæringen faktisk skal skjermes.",
    )

    /**
     * Klasser, interfaces, objects, funksjoner, properties og typealiaser, også nestede.
     * `declarations()` dekker alle disse i én sveip, så regelen kan ikke gå glipp av en erklæringstype fordi noen glemte å liste den opp.
     */
    private fun KoFileDeclaration.navngitteBrudd(modifikatorlinjer: List<Int>): List<String> =
        declarations(includeNested = true, includeLocal = true)
            .filterIsInstance<KoVisibilityModifierProvider>()
            .filter { erklæring -> erklæring.hasInternalModifier }
            .map { erklæring ->
                val navn = (erklæring as? KoNameProvider)?.name ?: "erklæring"
                "${erklæring.bruddsted(path, modifikatorlinjer)}: $navn"
            }

    /**
     * Konstruktører og settere er det eneste `declarations()` ikke returnerer, så de hentes eksplisitt.
     * Begge er navnløse hos Konsist, og beskrives derfor via klassen og propertyen de hører til.
     */
    private fun KoFileDeclaration.konstruktørOgSetterbrudd(modifikatorlinjer: List<Int>): List<String> =
        classes(includeNested = true).flatMap { klasse ->
            klasse.constructors
                .filter { konstruktør -> konstruktør.hasInternalModifier }
                .map { konstruktør -> "${konstruktør.bruddsted(path, modifikatorlinjer)}: konstruktøren i ${klasse.name}" }
        } +
            properties(includeNested = true).mapNotNull { property ->
                property.setter
                    ?.takeIf { setter -> setter.hasInternalModifier }
                    ?.let { setter -> "${setter.bruddsted(path, modifikatorlinjer)}: setteren til ${property.name}" }
            }

    /**
     * Linjenumrene i fila der `internal` faktisk står, med kommentarer hoppet over og strengliteraler maskert.
     * Konsists `location` peker på starten av erklæringen *inkludert* KDoc-en, så en erklæring med dokumentasjon rapporteres flere linjer for tidlig.
     * Meldingen skal peke på linja som skal endres, ikke på dokumentasjonen over den.
     */
    private fun KoFileDeclaration.modifikatorlinjer(): List<Int> = kodelinjer()
        .filter { (_, kode) -> internalRegex.containsMatchIn(kode) }
        .map { (linjenummer, _) -> linjenummer }

    private val internalRegex = Regex("""\binternal\b""")

    /**
     * `sti:linje` der modifikatoren står: første `internal`-linje fra og med der Konsist plasserer erklæringen.
     * Faller tilbake på Konsists egen posisjon hvis ingen slik linje finnes, slik at et brudd aldri rapporteres uten sted.
     */
    private fun KoVisibilityModifierProvider.bruddsted(filsti: String, modifikatorlinjer: List<Int>): String {
        val location = (this as? KoLocationProvider)?.location ?: return filsti
        val start = location.linjenummer() ?: return location
        return "$filsti:${modifikatorlinjer.firstOrNull { linje -> linje >= start } ?: start}"
    }

    /** `location` har formen `sti:linje:kolonne`, og stien kan selv inneholde kolon, så linjenummeret leses nest bakerst. */
    private fun String.linjenummer(): Int? = split(":").let { deler -> deler.getOrNull(deler.size - 2)?.toIntOrNull() }
}
