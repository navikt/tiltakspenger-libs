package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines
import kotlin.streams.asSequence

/**
 * Håndhever konvensjonen om én setning per linje i KDoc, kommentarer og markdown-filer.
 * Se «KDoc og kommentarer: én setning per linje» i AGENTS-backend.md i monorepo-rota.
 *
 * Konvensjonen har to halvdeler, og begge håndheves:
 * en linje skal ikke inneholde mer enn én setning, og en setning skal ikke brekkes over flere linjer.
 *
 * Deteksjonen er heuristikker.
 * Flere setninger på én linje flagges når en setningsavslutter (`.`, `!`, `?`) etterfølges av mellomrom og stor forbokstav.
 * Brukket setning flagges når en fortsettelseslinje starter med liten bokstav og forrige linje i samme avsnitt ikke er avsluttet.
 * Unntatt fra sjekkene er forkortelser, interjeksjoner, tall (listepunkter, datoer), sitater, innhold i backticks/kodeblokker/rå strenger og linjer som ser ut som kode.
 *
 * Kildekodesjekkene tar et Konsist-scope, markdownsjekkene tar en rotkatalog (typisk `Path.of(System.getProperty("user.dir"))` fra testen).
 */
object EnSetningPerLinje {

    fun flereSetningerIKommentarer(scope: KoScope): List<String> = scope.kildefiler().flatMap { file ->
        file.text
            .lines()
            .toKommentarlinjer()
            .filter { kommentarlinje -> kommentarlinje.tekst.harFlereSetninger() }
            .map { kommentarlinje -> "${file.path}:${kommentarlinje.linjenummer}: ${kommentarlinje.tekst}" }
    }

    fun brukneSetningerIKommentarer(scope: KoScope): List<String> = scope.kildefiler().flatMap { file ->
        file.text
            .lines()
            .toKommentarlinjer()
            .finnBrukneSetninger()
            .map { kommentarlinje -> "${file.path}:${kommentarlinje.linjenummer}: ${kommentarlinje.tekst}" }
    }

    fun flereSetningerIMarkdown(rot: Path, ekskluderteKataloger: Set<String> = standardEkskluderteKataloger): List<String> = rot
        .markdownFiler(ekskluderteKataloger)
        .flatMap { markdownFil ->
            markdownFil
                .readLines()
                .toMarkdownProsalinjer()
                .filter { markdownlinje -> markdownlinje.tekst.harFlereSetninger() }
                .map { markdownlinje -> "${rot.relativize(markdownFil)}:${markdownlinje.linjenummer}: ${markdownlinje.tekst}" }
        }.toList()

    fun brukneSetningerIMarkdown(rot: Path, ekskluderteKataloger: Set<String> = standardEkskluderteKataloger): List<String> = rot
        .markdownFiler(ekskluderteKataloger)
        .flatMap { markdownFil ->
            markdownFil
                .readLines()
                .toMarkdownProsalinjer()
                .finnBrukneSetninger()
                .map { markdownlinje -> "${rot.relativize(markdownFil)}:${markdownlinje.linjenummer}: ${markdownlinje.tekst}" }
        }.toList()

    fun assertFlereSetningerIKommentarer(scope: KoScope) = assertIngenBrudd(
        flereSetningerIKommentarer(scope),
        "Skriv én setning per linje i KDoc/kommentarer (se AGENTS-backend.md i monorepo-rota).",
    )

    fun assertBrukneSetningerIKommentarer(scope: KoScope) = assertIngenBrudd(
        brukneSetningerIKommentarer(scope),
        "Ikke brekk en setning over flere linjer i KDoc/kommentarer — slå sammen med forrige linje (se AGENTS-backend.md i monorepo-rota).",
    )

    fun assertFlereSetningerIMarkdown(rot: Path, ekskluderteKataloger: Set<String> = standardEkskluderteKataloger) = assertIngenBrudd(
        flereSetningerIMarkdown(rot, ekskluderteKataloger),
        "Skriv én setning per linje i markdown (se AGENTS-backend.md i monorepo-rota).",
    )

    fun assertBrukneSetningerIMarkdown(rot: Path, ekskluderteKataloger: Set<String> = standardEkskluderteKataloger) = assertIngenBrudd(
        brukneSetningerIMarkdown(rot, ekskluderteKataloger),
        "Ikke brekk en setning over flere linjer i markdown — slå sammen med forrige linje (se AGENTS-backend.md i monorepo-rota).",
    )

    private data class Tekstlinje(
        val linjenummer: Int,
        val tekst: String,
        val gruppe: Int,
    )

    /**
     * Trekker ut kommentarinnholdet (KDoc, blokkommentarer og `//`-kommentarer) fra kildekodelinjer.
     * Linjer inne i kodeblokker (```) i kommentarer og inne i rå strenger (tre anførselstegn) hoppes over.
     * [Tekstlinje.gruppe] identifiserer sammenhengende avsnitt: blanke linjer, kodeblokker og bytte av kommentarblokk starter en ny gruppe.
     * Trailing-kommentarer (kommentar etter kode på samme linje) isoleres i hver sin gruppe, siden nabolinjers kommentarer er uavhengige av hverandre.
     */
    private fun List<String>.toKommentarlinjer(): List<Tekstlinje> {
        val kommentarlinjer = mutableListOf<Tekstlinje>()
        var iBlokkommentar = false
        var iKodeblokk = false
        var iRåstreng = false
        var gruppe = 0

        forEachIndexed { index, linje ->
            val linjenummer = index + 1
            val trimmet = linje.trim()

            if (iRåstreng) {
                if (linje.harOddetallRåstrengmarkører()) {
                    iRåstreng = false
                }
                gruppe++
                return@forEachIndexed
            }

            val erBlokklinje = iBlokkommentar || trimmet.startsWith("/*")
            val kommentartekst =
                when {
                    iBlokkommentar -> {
                        if (trimmet.contains("*/")) iBlokkommentar = false
                        trimmet.substringBefore("*/").removePrefix("*").trim()
                    }

                    trimmet.startsWith("/*") -> {
                        if (!trimmet.contains("*/")) iBlokkommentar = true
                        trimmet
                            .removePrefix("/**")
                            .removePrefix("/*")
                            .substringBefore("*/")
                            .trim()
                    }

                    else -> linje.tekstEtterLinjekommentar()
                }
            val erTrailingKommentar = kommentartekst != null && !erBlokklinje && !trimmet.startsWith("//")

            when {
                kommentartekst == null -> {
                    gruppe++
                    iKodeblokk = false
                    if (linje.harOddetallRåstrengmarkører()) {
                        iRåstreng = true
                    }
                }

                kommentartekst.startsWith("```") -> {
                    iKodeblokk = !iKodeblokk
                    gruppe++
                }

                iKodeblokk -> {}

                kommentartekst.isEmpty() -> gruppe++

                erTrailingKommentar -> {
                    gruppe++
                    kommentarlinjer.add(Tekstlinje(linjenummer, kommentartekst, gruppe))
                    gruppe++
                }

                else -> kommentarlinjer.add(Tekstlinje(linjenummer, kommentartekst, gruppe))
            }
            if (trimmet.contains("*/")) {
                gruppe++
                iKodeblokk = false
            }
        }
        return kommentarlinjer
    }

    /**
     * Finner innholdet etter `//` på en kodelinje, eller null hvis linjen ikke har noen linjekommentar.
     * `//` inne i strengliteraler (typisk URL-er) filtreres bort med en enkel heuristikk: et oddetall anførselstegn foran, eller `:` rett foran.
     */
    private fun String.tekstEtterLinjekommentar(): String? {
        var searchFrom = 0
        while (true) {
            val index = indexOf("//", searchFrom)
            if (index == -1) return null
            val insideString = take(index).count { char -> char == '"' } % 2 == 1
            val partOfUrl = index > 0 && this[index - 1] == ':'
            if (!insideString && !partOfUrl) {
                return substring(index + 2).trim()
            }
            searchFrom = index + 2
        }
    }

    /**
     * Trekker ut prosalinjene fra en markdown-fil.
     * Kodeblokker (```) og tabellinjer hoppes over.
     * [Tekstlinje.gruppe] identifiserer sammenhengende avsnitt: blanke linjer, kodeblokker og tabeller starter en ny gruppe.
     */
    private fun List<String>.toMarkdownProsalinjer(): List<Tekstlinje> {
        val prosalinjer = mutableListOf<Tekstlinje>()
        var iKodeblokk = false
        var gruppe = 0

        forEachIndexed { index, linje ->
            val trimmet = linje.trim()
            if (trimmet.startsWith("```")) {
                iKodeblokk = !iKodeblokk
                gruppe++
                return@forEachIndexed
            }
            if (iKodeblokk || trimmet.startsWith("|") || trimmet.isEmpty()) {
                gruppe++
                return@forEachIndexed
            }
            prosalinjer.add(Tekstlinje(index + 1, trimmet, gruppe))
        }
        return prosalinjer
    }

    /**
     * Heuristikk for om en linje inneholder mer enn én setning.
     * Flagger setningsavslutter etterfulgt av mellomrom og stor forbokstav.
     * Unntak: forkortelser, interjeksjoner, tall og innhold i backticks eller sitater (som maskeres før sjekken).
     */
    private fun String.harFlereSetninger(): Boolean {
        val maskertTekst =
            replace(inlineKodespennRegex, "kode")
                .replace(sitatRegex, "sitat")
        return setningsskilleRegex.findAll(maskertTekst).any { match ->
            val avslutter = maskertTekst[match.range.first]
            val ordFørAvslutter =
                maskertTekst
                    .take(match.range.first)
                    .trimEnd { char -> char in avsluttendeTegn }
                    .takeLastWhile { char -> char.isLetterOrDigit() || char == '.' || char == '%' }
                    .trimStart('.')
                    .lowercase()

            when {
                ordFørAvslutter.isEmpty() -> false
                avslutter == '.' && ordFørAvslutter in forkortelser -> false
                avslutter == '.' && ordFørAvslutter.all { char -> char.isDigit() } -> false
                avslutter == '!' && ordFørAvslutter in interjeksjoner -> false
                else -> true
            }
        }
    }

    /**
     * Heuristikk for setninger som er brukket over flere linjer.
     * Flagger en fortsettelseslinje som starter med liten bokstav når forrige linje i samme avsnitt ikke er avsluttet.
     * Markørlinjer (listepunkter, KDoc-tags, overskrifter) starter aldri med liten bokstav og er dermed automatisk unntatt.
     * Linjer som ser ut som kode (utkommentert kode eller kodeeksempler uten gjerder) unntas også.
     */
    private fun List<Tekstlinje>.finnBrukneSetninger(): List<Tekstlinje> =
        zipWithNext().mapNotNull { (forrige, denne) ->
            denne.takeIf {
                denne.gruppe == forrige.gruppe &&
                    denne.tekst.first().isLowerCase() &&
                    forrige.tekst.erUavsluttet() &&
                    !forrige.tekst.erKodeaktig() &&
                    !denne.tekst.erKodeaktig()
            }
        }

    /** En linje regnes som avsluttet når siste tegn (etter stripping av avsluttende tegn) er setningsavslutter eller kolon. */
    private fun String.erUavsluttet(): Boolean {
        val sisteTegn = trimEnd { char -> char in avsluttendeTegn || char.isWhitespace() }.lastOrNull() ?: return false
        return sisteTegn !in ".!?:"
    }

    private fun String.erKodeaktig(): Boolean = kodeaktigRegex.containsMatchIn(this)

    private fun String.harOddetallRåstrengmarkører(): Boolean = råstrengRegex.findAll(this).count() % 2 == 1

    private fun Path.markdownFiler(ekskluderteKataloger: Set<String>): Sequence<Path> =
        Files
            .walk(this)
            .asSequence()
            .filter { path -> path.extension == "md" }
            .filterNot { path ->
                val relativePath = relativize(path).toString()
                ekskluderteKataloger.any { katalog -> relativePath == katalog || relativePath.startsWith("$katalog/") }
            }

    /** Setningsavslutter, eventuelle avsluttende tegn (sitat/parentes/utheving), mellomrom, eventuelle innledende tegn og stor forbokstav. */
    private val setningsskilleRegex = Regex("""[.!?]["'`»)\]*_]*\s+[\[("«*_]*\p{Lu}""")

    /** Tegn som kan stå inntil setningsavslutteren uten å være del av ordet (sitat/parentes/utheving/generics). */
    private val avsluttendeTegn = "\"'`»)]*_>"

    private val inlineKodespennRegex = Regex("""`[^`]+`""")

    /** Sitater («...» og "...") behandles som atomisk innhold på linje med kodespenn. */
    private val sitatRegex = Regex("""«[^»]+»|"[^"]+"""")

    /** Rå streng-markør (tre anførselstegn) — et oddetall på en kodelinje åpner eller lukker en rå streng. */
    private val råstrengRegex = Regex("\"{3}")

    /** Utkommentert kode og kodeeksempler: Kotlin-nøkkelord, kall-uttrykk eller tilordning. */
    private val kodeaktigRegex = Regex("""^(val|var|fun|if|when|for|while|return|import)\b|^[\w.]+\(.*\)$|\s=\s""")

    private val forkortelser =
        setOf(
            "f.eks", "bl.a", "dvs", "osv", "ca", "jf", "jfr", "nr", "evt", "e.l", "o.l",
            "mv", "m.m", "m.fl", "pga", "ift", "iht", "mht", "inkl", "hhv",
            "ifm", "mtp", "vha", "vedr", "ref",
            "e.g", "i.e", "etc", "vs",
        )

    private val interjeksjoner = setOf("nb", "obs", "viktig", "merk")

    val standardEkskluderteKataloger = setOf("build", ".gradle", ".git", ".idea", "node_modules")
}
