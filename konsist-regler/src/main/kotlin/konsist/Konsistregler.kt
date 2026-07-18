package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * Kildefilene i scopet, uten `.kt`-filer som ligger under resources.
 * Konsist tar med `.kt`-filer under `src/<sourceSet>/resources` i prosjekt-scopene, men slike filer er data (f.eks. testfixturene til reglene i denne modulen), ikke kildekode.
 * Alle reglene i modulen går via denne, slik at fixtures og annen ressurs-data aldri gir brudd.
 */
fun KoScope.kildefiler(): List<KoFileDeclaration> = files.filterNot { file ->
    "/src/test/resources/" in file.path || "/src/main/resources/" in file.path
}

/**
 * Kodelinjene i fila som (linjenummer, kode)-par for tekstbaserte regler.
 * Kommentarlinjer hoppes over, trailing-kommentarer strippes, og innholdet i inline-strengliteraler maskeres (tekst om et forbudt kall er ikke et kall).
 */
internal fun KoFileDeclaration.kodelinjer(): List<Pair<Int, String>> =
    text.lines().mapIndexedNotNull { index, linje ->
        val trimmet = linje.trim()
        if (trimmet.startsWith("//") || trimmet.startsWith("*") || trimmet.startsWith("/*")) {
            null
        } else {
            index + 1 to linje.utenTrailingKommentar().replace(strengliteralRegex, "\"\"")
        }
    }

private val strengliteralRegex = Regex(""""[^"]*"""")

/**
 * Kutter linjen ved første `//` som starter en trailing-kommentar.
 * `//` inne i strengliteraler (typisk URL-er) beholdes med en enkel heuristikk: oddetall anførselstegn foran, eller `:` rett foran.
 */
private fun String.utenTrailingKommentar(): String {
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

/**
 * Kaster [AssertionError] med [intro] og en punktliste over bruddene hvis [brudd] ikke er tom.
 * Felles feilrapportering for alle reglene i denne modulen, slik at meldingene ser like ut på tvers av repoer.
 */
fun assertIngenBrudd(brudd: List<String>, intro: String) {
    if (brudd.isEmpty()) return
    throw AssertionError(
        "$intro\nFant ${brudd.size} brudd:\n" + brudd.joinToString("\n") { "- $it" },
    )
}
