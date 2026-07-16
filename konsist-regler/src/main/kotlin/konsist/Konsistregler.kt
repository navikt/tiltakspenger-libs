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
 * Kaster [AssertionError] med [intro] og en punktliste over bruddene hvis [brudd] ikke er tom.
 * Felles feilrapportering for alle reglene i denne modulen, slik at meldingene ser like ut på tvers av repoer.
 */
fun assertIngenBrudd(brudd: List<String>, intro: String) {
    if (brudd.isEmpty()) return
    throw AssertionError(
        "$intro\nFant ${brudd.size} brudd:\n" + brudd.joinToString("\n") { "- $it" },
    )
}
