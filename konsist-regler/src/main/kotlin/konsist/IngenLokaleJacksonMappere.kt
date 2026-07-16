package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Jackson-mappere skal ikke konstrueres lokalt — bruk den delte `objectMapper` fra `tiltakspenger-libs/json`.
 *
 * Bakgrunn: Jackson 3 strammet inn getter-valideringen, slik at Kotlin-felter med ledende æøå (f.eks. `årMåned`) droppes stille fra serialisert JSON.
 * Den delte mapperen er beskyttet mot dette via `KotlinFeature.KotlinPropertyNameAsImplicitName` (låst av kontraktstest i libs/json).
 * En lokalt konstruert mapper mangler beskyttelsen og reintroduserer buggen uten feilmelding — se navikt/tiltakspenger#30.
 *
 * Deteksjonen er tekstbasert på kodelinjer (kommentarer strippes først), siden Konsist ikke modellerer kallsteder dypt nok til AST-match.
 * Innebygde unntak: pakkene under `no.nav.tiltakspenger.libs.json` (der den delte mapperen bor) og denne modulen selv (mønsterlisten under inneholder mønstrene som strenger).
 * Bevisste unntak i et repo legges i [brudd] sin `tillatteFiler` med filstier (suffiks-match), med en kommentar på kallstedet om hvorfor.
 */
object IngenLokaleJacksonMappere {

    /** Ordnet mest-spesifikk først, siden kun første treff per linje rapporteres (`ObjectMapper(` er substring av `jacksonObjectMapper(`). */
    private val forbudteMønstre = listOf(
        "JsonMapper.builder(",
        "JsonMapper.shared",
        "jacksonObjectMapper(",
        "jacksonMapperBuilder(",
        "ObjectMapper(",
        "jackson3 {",
        "jackson {",
    )

    private val tillattePakker = listOf(
        "no.nav.tiltakspenger.libs.json",
        "no.nav.tiltakspenger.libs.konsist",
    )

    fun brudd(scope: KoScope, tillatteFiler: Set<String> = emptySet()): List<String> = scope.kildefiler()
        .filterNot { file -> tillattePakker.any { pakke -> file.packagee?.name?.startsWith(pakke) == true } }
        .filterNot { file -> tillatteFiler.any { tillatt -> file.path.endsWith(tillatt) } }
        .flatMap { file ->
            file.text.lines().mapIndexedNotNull { index, linje ->
                val kode = linje.utenKommentarer() ?: return@mapIndexedNotNull null
                forbudteMønstre
                    .firstOrNull { mønster -> mønster in kode }
                    ?.let { mønster -> "${file.path}:${index + 1}: $mønster" }
            }
        }

    fun assert(scope: KoScope, tillatteFiler: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, tillatteFiler),
        "Ikke konstruer Jackson-mappere lokalt — bruk den delte objectMapper fra tiltakspenger-libs/json (se navikt/tiltakspenger#30).",
    )

    /**
     * Returnerer kodedelen av linjen, eller null hvis hele linjen er kommentar.
     * `//` inne i strengliteraler (typisk URL-er) regnes ikke som kommentarstart: et oddetall anførselstegn foran, eller `:` rett foran, hopper over.
     * Linjer inne i blokkommentarer detekteres ikke linjevis, men blokkommentar-linjer starter i praksis med `*` og fanges av prefikssjekken.
     */
    private fun String.utenKommentarer(): String? {
        val trimmet = trim()
        if (trimmet.startsWith("//") || trimmet.startsWith("*") || trimmet.startsWith("/*")) return null
        var searchFrom = 0
        while (true) {
            val index = indexOf("//", searchFrom)
            if (index == -1) return this
            val insideString = take(index).count { char -> char == '"' } % 2 == 1
            val partOfUrl = index > 0 && this[index - 1] == ':'
            if (!insideString && !partOfUrl) return take(index)
            searchFrom = index + 2
        }
    }
}
