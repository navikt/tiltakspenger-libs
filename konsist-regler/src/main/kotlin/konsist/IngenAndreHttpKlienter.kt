package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readLines

/**
 * HTTP-kall mot andre tjenester går via `httpklient`-modulen i libs, aldri direkte via en annen HTTP-klient.
 * Regelen dekker tre av de fire måtene en klient kan snike seg inn på: som import, som fullkvalifisert kall, og som avhengighet i byggfila.
 *
 * [assertIngenKlienterIProduksjonskode] er hovedregelen og forbyr alle kjente klient-API-er.
 * [assertIngenKlienterITestkode] er strengt tatt en delmengde: den forbyr klientmotorer og fremmede klientbiblioteker, men tillater ktor sin `testApplication`-klient (`io.ktor.client.request`/`statement`/`call`), som er eneste vei inn til test-serveren, og JDK-typene `HttpRequest`/`HttpResponse` som vår egen transportkontrakt eksponerer.
 * [assertIngenKlientavhengigheter] leser byggfilene og fanger avhengigheter som er deklarert uten å være tatt i bruk ennå.
 *
 * Den fjerde måten — transitivt innslep — kan ikke sjekkes herfra, og hører hjemme i en Gradle-task mot `runtimeClasspath` i hvert repo.
 * Grunnen er at test-classpathen ikke er en meningsfull flate: `ktor-server-test-host` drar inn både `ktor-client-core` og `ktor-client-apache5` (og dermed Apache HttpClient 5) by design, så en `Class.forName`-sjekk fra en test ville flagget dem i hele flåten.
 *
 * HTTP-vokabular utenfor klientpakkene er bevisst tillatt — f.eks. `io.ktor.http.ContentType`, `io.ktor.http.HttpHeaders` og `java.net.URI`.
 * Legitime unntak er implementasjonen selv (`httpklient`-infrastrukturen bygger transporten på JDK-klienten) og testhjelpere mot ktor sin `testApplication`.
 * Kalleren velger scope (typisk `scopeFromProduction()`) og unntar slike filer via scope-slicing eller `unntatteFilstier` (sti-suffikser), f.eks. for klienter som ennå ikke er migrert.
 */
object IngenAndreHttpKlienter {

    /**
     * Klientbiblioteker fra tredjepart, forbudt i all kildekode.
     * Ingen av dem har en legitim bruk hos oss — verken i produksjon eller test.
     */
    private val tredjepartsklienter = listOf(
        "okhttp3.",
        "com.squareup.okhttp",
        "retrofit2.",
        "org.apache.hc.",
        "org.apache.http.",
        "com.github.kittinunf.fuel",
        "kong.unirest.",
        "io.vertx.ext.web.client",
        "jakarta.ws.rs.client.",
        "javax.ws.rs.client.",
        "org.springframework.web.client.",
        "org.springframework.web.reactive.function.client.",
        "org.http4k.client.",
        "feign.",
    )

    /**
     * Ktor sine klientmotorer, forbudt også i testkode.
     * Motor-importen er det som skiller en ekte nettverksklient fra `testApplication`-klienten, som kjører i minnet uten sokkel.
     * Dekker også `MockEngine`: en klient som må stubbes med motor-mock skal i stedet være vår egen klient over `FakeHttpTransport`.
     */
    private val ktorKlientmotorer = listOf("io.ktor.client.engine.")

    /**
     * `URLConnection`-familien i JDK-et.
     * Ligger utenfor `java.net.http` og må derfor listes for seg.
     */
    private val urlConnection = listOf("java.net.HttpURLConnection", "java.net.URLConnection")

    /** Alt som er forbudt i produksjonskode: hele ktor-klienten, hele JDK-klienten og alle tredjepartsklientene. */
    private val forbudtIProduksjonskode =
        tredjepartsklienter + ktorKlientmotorer + urlConnection + listOf("io.ktor.client.", "java.net.http.")

    /**
     * Alt som er forbudt i testkode.
     * `java.net.http.HttpClient` er selve klienten og forbudt, mens `HttpRequest`/`HttpResponse` er kontraktstypene `HttpTransport` og `FakeHttpTransport` eksponerer og derfor tillatt.
     */
    private val forbudtITestkode =
        tredjepartsklienter + ktorKlientmotorer + urlConnection + listOf("java.net.http.HttpClient")

    /**
     * Koordinater og versjonskatalog-aliaser som ikke skal stå i en byggfil.
     * Fanger avhengigheter som er deklarert uten å være tatt i bruk — de er dødvekt i dag og en åpen dør i morgen.
     *
     * Ktor-artefaktene er listet enkeltvis, ikke som prefikset `io.ktor:ktor-client`.
     * Motorene og kjernen skal aldri deklareres, mens plugin-artefakter som `ktor-client-content-negotiation` er legitime i testscope: de konfigurerer `testApplication`-klienten, som ikke er en nettverksklient.
     */
    private val forbudteKoordinater = listOf(
        "io.ktor:ktor-client-core",
        "io.ktor:ktor-client-cio",
        "io.ktor:ktor-client-apache",
        "io.ktor:ktor-client-okhttp",
        "io.ktor:ktor-client-java",
        "io.ktor:ktor-client-android",
        "io.ktor:ktor-client-mock",
        "io.ktor:ktor-client-logging",
        "com.squareup.okhttp3",
        "com.squareup.retrofit2",
        "org.apache.httpcomponents",
        "com.github.kittinunf.fuel",
        "com.konghq:unirest",
        "io.vertx:vertx-web-client",
        "org.http4k:http4k-client",
        "io.github.openfeign",
        "libs.ktor.client.core",
        "libs.ktor.client.cio",
        "libs.ktor.client.mock",
        "libs.okhttp",
        "libs.retrofit",
        "libs.fuel",
        "libs.unirest",
    )

    fun klienterIProduksjonskode(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> =
        kildekodebrudd(scope, forbudtIProduksjonskode, unntatteFilstier)

    fun klienterITestkode(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> =
        kildekodebrudd(scope, forbudtITestkode, unntatteFilstier)

    /**
     * Byggfilene under [rot] (`build.gradle.kts` og `gradle/libs.versions.toml`) som deklarerer en forbudt klientavhengighet.
     * Kun linjer som faktisk deklarerer en avhengighet teller — en koordinat nevnt i en vanlig liste er ikke en avhengighet, og en byggfil som forbyr klienter må kunne navngi dem.
     * Kommentarlinjer og `exclude(...)`-linjer hoppes over av samme grunn: å nevne en koordinat for å utelate den er nettopp det vi vil ha.
     * Filer under `src/<sourceSet>/resources` er data (f.eks. testfixturene til denne regelen), ikke byggfiler, og hoppes alltid over — samme prinsipp som [kildefiler].
     *
     * Begrensning: koordinaten må stå på samme linje som konfigurasjonsnavnet, altså `implementation("gruppe:artefakt:versjon")`, som er formen hele flåten bruker.
     */
    fun klientavhengigheter(rot: Path, unntatteFilstier: Set<String> = emptySet()): List<String> =
        rot
            .filerUnder(standardEkskluderteKataloger) { path -> path.name == "build.gradle.kts" || path.name == "libs.versions.toml" }
            .filterNot { fil ->
                val relativStreng = rot.relativize(fil).toString()
                "src/main/resources/" in relativStreng || "src/test/resources/" in relativStreng
            }.filterNot { fil -> unntatteFilstier.any { sti -> fil.toString().endsWith(sti) } }
            .flatMap { fil ->
                fil.readLines().mapIndexedNotNull { index, linje ->
                    val trimmet = linje.trim()
                    if (trimmet.startsWith("//") || trimmet.startsWith("#") || "exclude(" in trimmet || !deklarasjonsRegex.containsMatchIn(trimmet)) {
                        null
                    } else {
                        forbudteKoordinater
                            .firstOrNull { koordinat -> koordinat in trimmet }
                            ?.let { koordinat -> "${rot.relativize(fil)}:${index + 1}: $koordinat" }
                    }
                }
            }.toList()

    fun assertIngenKlienterIProduksjonskode(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        klienterIProduksjonskode(scope, unntatteFilstier),
        "HTTP-kall går via libs sin httpklient. Følgende importer av andre HTTP-klienter er ikke tillatt.",
    )

    fun assertIngenKlienterITestkode(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        klienterITestkode(scope, unntatteFilstier),
        "Testkode driver test-serveren med ktor sin testApplication-klient og eksterne kall med FakeHttpTransport. Følgende klientmotorer og klientbiblioteker er ikke tillatt.",
    )

    fun assertIngenKlientavhengigheter(rot: Path, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        klientavhengigheter(rot, unntatteFilstier),
        "Byggfilene skal ikke deklarere andre HTTP-klienter enn libs sin httpklient.",
    )

    private fun kildekodebrudd(scope: KoScope, forbudtePrefikser: List<String>, unntatteFilstier: Set<String>): List<String> =
        scope
            .kildefiler()
            .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
            .flatMap { file -> file.importbrudd(forbudtePrefikser) + file.tekstbrudd(forbudtePrefikser) }

    private fun KoFileDeclaration.importbrudd(forbudtePrefikser: List<String>): List<String> =
        imports
            .filter { import -> forbudtePrefikser.any { prefiks -> import.name.startsWith(prefiks) } }
            .map { import -> "$path: ${import.name}" }

    /**
     * Klientbruk som ikke går gjennom en import: fullkvalifiserte kall og `URL(...).openConnection()`.
     * Kommentarer og strengliteraler er allerede filtrert bort av [kodelinjer], så KDoc som omtaler en klient gir ikke brudd.
     * Import-linjene hoppes over her, siden de dekkes av [importbrudd] og ellers ville blitt rapportert to ganger.
     */
    private fun KoFileDeclaration.tekstbrudd(forbudtePrefikser: List<String>): List<String> {
        // Unntaket for classpath-ressurser gjelder hele fila, ikke linjen, fordi `getResource(...)` og `.openStream()` ofte står på hver sin linje.
        val leserClasspathRessurs = "getResource" in text
        return kodelinjer()
            .filterNot { (_, kode) -> kode.trimStart().startsWith("import ") }
            .mapNotNull { (linjenummer, kode) ->
                val treff = forbudtePrefikser.firstOrNull { prefiks -> prefiks in kode }
                    ?: kode.nettverksåpning(leserClasspathRessurs)
                treff?.let { "$path:$linjenummer: $it" }
            }
    }

    /**
     * `openConnection()`/`openStream()` på en URL åpner et nettverkskall utenom httpklient, uten at noen import avslører det.
     * `openStream()` flagges ikke i filer som leser classpath-ressurser, siden `getResource(...).openStream()` leser en fil og ikke et nettverk.
     * `openConnection()` flagges uansett: den har ingen tilsvarende legitim bruk hos oss.
     */
    private fun String.nettverksåpning(leserClasspathRessurs: Boolean): String? {
        val treff = nettverksåpningRegex.find(this)?.value ?: return null
        return treff.takeUnless { leserClasspathRessurs && "openStream" in it }
    }

    private val nettverksåpningRegex = Regex("""\.open(Connection|Stream)\(""")

    /** En avhengighetsdeklarasjon: et Gradle-konfigurasjonsnavn med parentes, eller `module = ` i en versjonskatalog. */
    private val deklarasjonsRegex =
        Regex("""\b(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|testCompileOnly|testFixtures|classpath|platform)\s*\(|\bmodule\s*=""")
}
