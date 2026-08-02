package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * Kataloger som inneholder en annen utsjekk av repoet, typisk et git-arbeidstre lagt under repo-rota (`.worktrees/<gren>/`).
 * Filene der tilhører en annen gren, og skal aldri påvirke reglene i arbeidskopien de tilfeldigvis ligger inni.
 * Begge skrivemåtene er med fordi repoenes `.gitignore` allerede ignorerer begge.
 *
 * Uten dette henter `Konsist.scopeFromProject()` inn arbeidstreets kildesett som om det var en egen modul.
 * En regel som ble skjerpet på hovedgrenen feiler da lokalt på et arbeidstre som ennå ikke er rebaset, mens CI er grønn — og hovedtreet er blokkert av kode det ikke eier.
 */
val ekskluderteUtsjekker = setOf(".worktrees", ".worktree")

/**
 * Kataloger som aldri inneholder kildekode eller konfigurasjon vi eier, og som de filbaserte reglene alltid hopper over.
 */
val standardEkskluderteKataloger = setOf("build", ".gradle", ".git", ".idea", "node_modules") + ekskluderteUtsjekker

/**
 * Filene under rota som [predikat] godtar, minus alt under [ekskluderteKataloger].
 * Ekskluderingen er segmentbasert slik at også nestede kataloger treffes (f.eks. `<modul>/build/` når rota er repo-rota, ikke bare `build/` på toppnivå).
 * Brukes av reglene som leser filer direkte fra disk i stedet for gjennom et Konsist-scope (markdown og byggfiler).
 */
internal fun Path.filerUnder(ekskluderteKataloger: Set<String>, predikat: (Path) -> Boolean): Sequence<Path> =
    Files
        .walk(this)
        .asSequence()
        .filter(predikat)
        .filterNot { path -> relativize(path).any { segment -> segment.toString() in ekskluderteKataloger } }

/**
 * Kildefilene i scopet, uten `.kt`-filer som ligger under resources, og uten filer som tilhører en annen utsjekk ([ekskluderteUtsjekker]).
 * Konsist tar med `.kt`-filer under `src/<sourceSet>/resources` i prosjekt-scopene, men slike filer er data (f.eks. testfixturene til reglene i denne modulen), ikke kildekode.
 * Alle reglene i modulen går via denne, så filtreringen gjelder uansett hvilket scope kalleren sender inn.
 *
 * Merk at `build` bevisst ikke filtreres her: Konsist-scopene inneholder ikke byggutdata, og testfixturene i denne modulen leses nettopp fra `build/resources/test`.
 */
fun KoScope.kildefiler(): List<KoFileDeclaration> = files.filterNot { file ->
    "/src/test/resources/" in file.path ||
        "/src/main/resources/" in file.path ||
        ekskluderteUtsjekker.any { katalog -> "/$katalog/" in file.path }
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

internal val strengliteralRegex = Regex(""""[^"]*"""")

/**
 * Kutter linjen ved første `//` som starter en trailing-kommentar.
 * `//` inne i strengliteraler (typisk URL-er) beholdes med en enkel heuristikk: oddetall anførselstegn foran, eller `:` rett foran.
 */
internal fun String.utenTrailingKommentar(): String {
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
