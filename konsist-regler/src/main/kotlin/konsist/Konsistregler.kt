/*
 * Delt fundament for reglene i modulen.
 *
 * Reglene som matcher på en liste av elementer (pakkesegmenter, forbudte navn, koordinater, markører) eksponerer lista som en public `standard…`-verdi, og tar et `ekstra…`-argument som legges til den.
 * Kalleren kan altså utvide det flåten har blitt enige om, men ikke erstatte det: en delt regel skal ikke kunne svekkes stille fra ett repo.
 * Trenger et repo å slippe unna et enkelttilfelle, er whitelisten (`unntatteFilstier`) veien — den er synlig, begrunnet på kallstedet og holdes ærlig av `assertWhitelistenErRyddet`.
 */
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
 * Et repo med en egen byggutdata-katalog legger den til med `ekstraEkskluderteKataloger`; settet kan ikke erstattes, slik at [ekskluderteUtsjekker] alltid blir med.
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
    kodelinjerMedStrenger().map { (linjenummer, kode) -> linjenummer to kode.replace(strengliteralRegex, "\"\"") }

/**
 * Som [kodelinjer], men uten maskering av strengliteraler.
 * Brukes av reglene der innholdet i strengen *er* det som skal leses — SQL-en et repo skriver bor nettopp i strengliteraler, og maskeringen ville gjort en slik regel blind.
 * Kommentarlinjer hoppes fortsatt over, slik at dokumentasjon som viser mønsteret den advarer mot ikke blir et brudd i seg selv.
 */
internal fun KoFileDeclaration.kodelinjerMedStrenger(): List<Pair<Int, String>> =
    text.lines().mapIndexedNotNull { index, linje ->
        val trimmet = linje.trim()
        if (trimmet.startsWith("//") || trimmet.startsWith("*") || trimmet.startsWith("/*")) {
            null
        } else {
            index + 1 to linje.utenTrailingKommentar()
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

/**
 * Vakt mot en vakuøs grønn kjøring: en regel som ikke finner noen filer å se på, består trivielt.
 * Et tomt eller feilrettet scope er ikke hypotetisk — `scopeFromProject()`/`scopeFromProduction()` leter etter en `.git`-*katalog*, og i et git-arbeidstre er `.git` en fil.
 * Da skanner Konsist feil tre eller ingenting, og hele regelsettet er grønt uten å ha sett koden.
 * En skrivefeil i pakkenavnet eller modulstien kalleren filtrerer på gir nøyaktig samme stillhet.
 *
 * Brukes av reglene som ser på et *utvalg* av scopet (én pakke, ett mønster), der utvalget kan bli tomt uten at noe annet slår ut.
 * [minstAntall] er hva repoet vet at det har: velg et tall trygt under dagens antall, men over null.
 */
fun assertSkanningenTraff(antall: Int, minstAntall: Int, hva: String) = assertIngenBrudd(
    listOfNotNull("fant $antall $hva".takeIf { antall < minstAntall }),
    "Skanningen fant færre enn $minstAntall $hva, så regelen sier ingenting. Sjekk at scopet og filteret peker på riktig tre.",
)

/**
 * Ratchet-en for reglene som tar en whitelist: en fil som ikke lenger bryter regelen, skal ut av whitelisten.
 * Uten den blir en ryddet fil liggende som et unntak ingen ser, og dekker stilltiende over neste brudd i samme fil.
 * Den fanger også oppføringer som aldri traff — en feilstavet eller utdatert sti er et unntak uten virkning, og whitelisten lyver om hva som gjenstår.
 *
 * [bruddUtenUnntak] er regelens egen `brudd()`-funksjon kalt med tom whitelist: differansen mot [unntatteFilstier] er nettopp oppføringene som ikke lenger trengs.
 * Alle reglene i modulen rapporterer brudd som `<filsti>:...`, og sti-suffiksene sammenlignes mot det, så matchingen blir like presis som regelens egen `endsWith`.
 */
fun assertWhitelistenErRyddet(unntatteFilstier: Set<String>, bruddUtenUnntak: List<String>) = assertIngenBrudd(
    unntatteFilstier.filterNot { sti -> bruddUtenUnntak.any { brudd -> "$sti:" in brudd } },
    "Whitelisten inneholder stier som ikke bryter regelen. Ta dem ut — en oppføring uten virkning dekker over neste brudd i samme fil.",
)
