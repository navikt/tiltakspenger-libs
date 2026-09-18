/*
 * Delt fundament for reglene i modulen.
 *
 * Regler som matcher på en liste av pakkesegmenter, forbudte navn, koordinater eller markører eksponerer lista som en public `standard…`-verdi og tar et `ekstra…`-argument i tillegg.
 * Kalleren kan utvide lista, men ikke erstatte den, slik at et enkelt repo ikke kan svekke en delt regel.
 * Enkelttilfeller tas ut med whitelisten `unntatteFilstier`, som står på kallstedet og holdes ryddig av `assertWhitelistenErRyddet`.
 */
package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * Kataloger som inneholder en annen utsjekk av repoet, typisk et git-arbeidstre under repo-rota (`.worktrees/<gren>/`).
 * Filene der hører til en annen gren og skal ikke påvirke reglene i utsjekken som kjører.
 * Begge skrivemåtene er med fordi `.gitignore` i repoene ignorerer begge.
 *
 * Uten filteret tar `Konsist.scopeFromProject()` med arbeidstreets kilder som om de var en egen modul.
 * En regel som er skjerpet på hovedgrenen feiler da lokalt på et arbeidstre som ikke er rebaset ennå, mens CI er grønn.
 *
 * Katalognavnene måles fra skanningsrota og ikke i den absolutte stien, se [kildefiler].
 */
val ekskluderteUtsjekker = setOf(".worktrees", ".worktree")

/**
 * Kataloger de filbaserte reglene hopper over, fordi de ikke inneholder kildekode eller konfigurasjon teamet eier.
 * Et repo med egen byggutdata-katalog legger den til med `ekstraEkskluderteKataloger`.
 * Settet kan ikke erstattes, så [ekskluderteUtsjekker] blir med uansett.
 */
val standardEkskluderteKataloger = setOf("build", ".gradle", ".git", ".idea", "node_modules") + ekskluderteUtsjekker

/**
 * Filene under rota som [predikat] godtar, minus alt under [ekskluderteKataloger].
 * Ekskluderingen ser på hvert segment i den relative stien, så `<modul>/build/` treffer og ikke bare `build/` på toppnivå.
 * Brukes av reglene som leser markdown og byggfiler fra disk i stedet for gjennom et Konsist-scope.
 */
internal fun Path.filerUnder(ekskluderteKataloger: Set<String>, predikat: (Path) -> Boolean): Sequence<Path> =
    Files
        .walk(this)
        .asSequence()
        .filter(predikat)
        .filterNot { path -> relativize(path).any { segment -> segment.toString() in ekskluderteKataloger } }

/**
 * Kildefilene i scopet, uten `.kt`-filer under resources og uten filer fra en annen utsjekk ([ekskluderteUtsjekker]).
 * Konsist tar med `.kt`-filer under `src/<sourceSet>/resources` i prosjekt-scopene, men i denne modulen er de testfixturer og ikke kildekode.
 * Alle reglene går via denne, så filtreringen gjelder uansett hvilket scope kalleren sender inn.
 *
 * `build` filtreres ikke bort, fordi Konsist-scopene ikke inneholder byggutdata og testfixturene leses fra `build/resources/test`.
 */
fun KoScope.kildefiler(): List<KoFileDeclaration> = kildefiler(skanningsrot())

/**
 * Som [kildefiler], men med rota oppgitt av kalleren, slik at begge retningene kan testes.
 *
 * Stien måles fra [rot] og ikke i absolutt form.
 * Kjører bygget fra et arbeidstre på `<repo>/.worktrees/<gren>/`, ligger `.worktrees` over rota, og bare utsjekker under rota skal utelates.
 * Et substring-søk i den absolutte stien så ikke forskjellen og tømte hele scopet.
 *
 * Ligger fila utenfor [rot], som i et [com.lemonappdev.konsist.api.Konsist.scopeFromExternalDirectory]-scope, finnes det ingen rot å måle mot, og hele den absolutte stien brukes.
 */
internal fun KoScope.kildefiler(rot: Path): List<KoFileDeclaration> {
    val kildefiler = files.filterNot { file ->
        val relativ = Path.of(file.path).ekteSti().settFra(rot)
        val sti = "/" + relativ.joinToString("/")
        "/src/test/resources/" in sti ||
            "/src/main/resources/" in sti ||
            relativ.any { segment -> segment.toString() in ekskluderteUtsjekker }
    }
    assertFilteretIkkeTømteScopet(antallFør = files.size, antallEtter = kildefiler.size, rot = rot, eksempelfil = files.firstOrNull()?.path)
    return kildefiler
}

/**
 * Rota Konsist bygde scopet fra.
 * `Konsist.projectRootPath` er offentlig API og gir repo-rota også i et flermodul-repo, der testtaskens arbeidskatalog er modulkatalogen.
 */
private fun skanningsrot(): Path = Path.of(Konsist.projectRootPath).ekteSti()

/**
 * Absolutt og normalisert sti med symlenker løst opp, slik at rot og fil kan sammenlignes.
 * På macOS er `/tmp` en symlenke til `/private/tmp`, så `/tmp/repo/Fil.kt` og rota `/private/tmp/repo` er ellers to ulike stier.
 * Finnes ikke stien på disk, brukes den normaliserte formen.
 */
internal fun Path.ekteSti(): Path {
    val absolutt = toAbsolutePath().normalize()
    return runCatching { absolutt.toRealPath() }.getOrDefault(absolutt)
}

/** Stien målt fra [rot], eller stien selv hvis den ligger utenfor rota. */
internal fun Path.settFra(rot: Path): Path = if (startsWith(rot)) rot.relativize(this) else this

/**
 * Feiler når filteret fjernet alle filene i et scope som ikke var tomt.
 * Reglene får da ingenting å lese, finner null brudd og blir grønne.
 * I et arbeidstre fant `scopeFromProject()` 542 filer, og `kildefiler()` leverte 0.
 *
 * Sjekken ligger her og ikke i testklassene, fordi alle reglene går via [kildefiler].
 * En testklasse som kaller et regelobjekt direkte er dekket uten å gjøre noe selv.
 *
 * Et scope som var tomt fra før feiler ikke.
 * Et `slice { }` på en modul repoet ikke har, eller et testkildesett som ikke finnes ennå, er en gyldig tilstand.
 * Derfor trengs det ikke noe unntak på kallstedet.
 */
private fun assertFilteretIkkeTømteScopet(antallFør: Int, antallEtter: Int, rot: Path, eksempelfil: String?) {
    if (antallFør == 0 || antallEtter > 0) return
    throw AssertionError(
        "Filteret i kildefiler() fjernet alle $antallFør filene i scopet, så reglene ville blitt grønne uten å lese kode.\n" +
            "Skanningsrot: $rot\n" +
            "Eksempelfil: $eksempelfil\n" +
            "Sjekk at rota peker på treet som kjører, og at filene ikke ligger under ${ekskluderteUtsjekker.joinToString(" eller ")} sett fra rota.",
    )
}

/**
 * Kodelinjene i fila som (linjenummer, kode)-par for tekstbaserte regler.
 * Kommentarlinjer hoppes over, trailing-kommentarer strippes, og innholdet i strengliteraler maskeres, slik at tekst om et forbudt kall ikke teller som kallet.
 */
internal fun KoFileDeclaration.kodelinjer(): List<Pair<Int, String>> =
    kodelinjerMedStrenger().map { (linjenummer, kode) -> linjenummer to kode.replace(strengliteralRegex, "\"\"") }

/**
 * Som [kodelinjer], men uten maskering av strengliteraler.
 * Brukes av reglene som skal lese innholdet i strengen, typisk SQL, der maskeringen ville fjernet det regelen ser etter.
 * Kommentarlinjer hoppes fortsatt over, så dokumentasjon som viser mønsteret den advarer mot ikke blir et brudd.
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
 * `//` inne i strengliteraler, typisk URL-er, beholdes når det står oddetall anførselstegn foran eller `:` rett foran.
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
 * Kaster [AssertionError] med [intro] og en punktliste over [brudd], hvis lista ikke er tom.
 * Felles feilrapportering for reglene i modulen, slik at meldingene ser like ut på tvers av repoer.
 */
fun assertIngenBrudd(brudd: List<String>, intro: String) {
    if (brudd.isEmpty()) return
    throw AssertionError(
        "$intro\nFant ${brudd.size} brudd:\n" + brudd.joinToString("\n") { "- $it" },
    )
}

/**
 * Feiler når en regel fant færre enn [minstAntall] elementer å se på.
 * En skrivefeil i pakkenavnet eller modulstien kalleren filtrerer på gir et tomt utvalg og en regel som består uten å ha sett noe.
 * At selve scopet er tømt fanges av [kildefiler]; denne sjekken dekker utvalget kalleren gjør etterpå.
 *
 * Brukes av reglene som ser på et utvalg av scopet, én pakke eller ett mønster, der utvalget kan bli tomt uten at noe annet slår ut.
 * [minstAntall] er hva repoet vet at det har, altså et tall under dagens antall og over null.
 */
fun assertSkanningenTraff(antall: Int, minstAntall: Int, hva: String) = assertIngenBrudd(
    listOfNotNull("fant $antall $hva".takeIf { antall < minstAntall }),
    "Skanningen fant færre enn $minstAntall $hva, så regelen sier ingenting. Sjekk at scopet og filteret peker på riktig tre.",
)

/**
 * Krever at en fil som ikke lenger bryter regelen, tas ut av whitelisten.
 * Blir den liggende, er den et unntak ingen ser, og den dekker over neste brudd i samme fil.
 * Sjekken fanger også oppføringer som aldri traff, altså feilstavede eller utdaterte stier.
 *
 * [bruddUtenUnntak] er regelens egen `brudd()` kalt med tom whitelist, så differansen mot [unntatteFilstier] er oppføringene som ikke trengs.
 * Reglene rapporterer brudd som `<filsti>:...`, og sti-suffiksene sammenlignes mot det, med samme presisjon som regelens egen `endsWith`.
 */
fun assertWhitelistenErRyddet(unntatteFilstier: Set<String>, bruddUtenUnntak: List<String>) = assertIngenBrudd(
    unntatteFilstier.filterNot { sti -> bruddUtenUnntak.any { brudd -> "$sti:" in brudd } },
    "Whitelisten inneholder stier som ikke bryter regelen. Ta dem ut; en oppføring uten virkning dekker over neste brudd i samme fil.",
)
