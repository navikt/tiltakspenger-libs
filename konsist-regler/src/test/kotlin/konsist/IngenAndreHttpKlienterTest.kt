package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class IngenAndreHttpKlienterTest {
    private val produksjonsscope = fixtureScope("httpklienter")
    private val testscope = fixtureScope("httpklienttest")

    @Test
    fun `flagger klient-importer i produksjonskode, men ikke http-vokabular eller httpklient selv`() {
        val samlet = IngenAndreHttpKlienter.klienterIProduksjonskode(produksjonsscope).joinToString("\n")

        samlet shouldContain "io.ktor.client.HttpClient"
        samlet shouldContain "io.ktor.client.engine.cio.CIO"
        samlet shouldContain "io.ktor.client.request.get"
        samlet shouldContain "java.net.http.HttpRequest"
        samlet shouldContain "java.net.HttpURLConnection"
        samlet shouldContain "okhttp3.OkHttpClient"
        samlet shouldContain "retrofit2.Retrofit"
        samlet shouldContain "org.apache.hc.client5.http.classic.HttpClient"
        samlet shouldNotContain "Ren.kt"
    }

    @Test
    fun `flagger fullkvalifisert klientbruk og nettverksåpning uten import`() {
        val brudd = IngenAndreHttpKlienter.klienterIProduksjonskode(produksjonsscope)

        // De åtte importene, pluss det fullkvalifiserte kallet og nettverksåpningen som ingen import avslører.
        brudd shouldHaveSize 10
        brudd.joinToString("\n") shouldContain ".openConnection("
    }

    @Test
    fun `unntatte filstier flagges ikke, og classpath-ressurser er ikke nettverkskall`() {
        IngenAndreHttpKlienter
            .klienterIProduksjonskode(produksjonsscope, unntatteFilstier = setOf("httpklienter/Brudd.kt"))
            .shouldBeEmpty()
    }

    @Test
    fun `testkode får bruke testApplication-klienten og transportkontrakten`() {
        val brudd = IngenAndreHttpKlienter.klienterITestkode(testscope)

        brudd shouldHaveSize 3
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "io.ktor.client.engine.mock.MockEngine"
        samlet shouldContain "okhttp3.OkHttpClient"
        samlet shouldContain "java.net.http.HttpClient"
        samlet shouldNotContain "Ren.kt"
    }

    @Test
    fun `ekstra forbudte prefikser utvider standardsettet`() {
        val brudd = IngenAndreHttpKlienter.klienterITestkode(testscope, ekstraForbudtePrefikser = listOf("io.ktor.client.request."))

        val samlet = brudd.joinToString("\n")
        samlet shouldContain "io.ktor.client.request.get"
        samlet shouldContain "Ren.kt"
    }

    @Test
    fun `flagger klientavhengigheter i byggfiler, men ikke kommentarer, exclude eller ren omtale`() {
        val brudd = IngenAndreHttpKlienter.klientavhengigheter(fixturesti("byggfiler"))

        brudd shouldHaveSize 3
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "appen/build.gradle.kts:2: io.ktor:ktor-client"
        samlet shouldContain "appen/build.gradle.kts:3: libs.okhttp"
        samlet shouldContain "gradle/libs.versions.toml:5: io.ktor:ktor-client"
        samlet shouldNotContain "retrofit"
        samlet shouldNotContain "ren/build.gradle.kts"
    }

    @Test
    fun `ekstra forbudte koordinater utvider standardsettet`() {
        val brudd = IngenAndreHttpKlienter.klientavhengigheter(
            fixturesti("byggfiler"),
            ekstraForbudteKoordinater = listOf("io.ktor:ktor-client-content-negotiation"),
        )

        brudd shouldHaveSize 4
        brudd.joinToString("\n") shouldContain "ren/build.gradle.kts"
    }

    /**
     * Markøren finnes for constraints som pinner en forbudt klient bort fra en sårbarhet, slik plattform-BOM-en gjør for HttpComponents.
     * Begge halvdelene av kontrakten testes her: en markør med begrunnelse unntar sin egen linje, og en markør uten begrunnelse unntar ingenting.
     */
    @Test
    fun `unntaksmarkør med begrunnelse unntar linja, uten begrunnelse gjør den ikke`() {
        val brudd = IngenAndreHttpKlienter.klientavhengigheter(fixturesti("byggfilerunntak"))

        brudd shouldHaveSize 2
        val samlet = brudd.joinToString("\n")
        // Markør uten tekst etter kolonet, og en oppføring uten markør på linja under en som har den.
        samlet shouldContain "appen/build.gradle.kts:3: com.squareup.okhttp3"
        samlet shouldContain "gradle/libs.versions.toml:7: org.apache.httpcomponents"
        samlet shouldNotContain "appen/build.gradle.kts:7"
        samlet shouldNotContain "gradle/libs.versions.toml:6"
    }

    @Test
    fun `unntatte filstier flagges ikke i byggfiler`() {
        IngenAndreHttpKlienter
            .klientavhengigheter(
                fixturesti("byggfiler"),
                unntatteFilstier = setOf("appen/build.gradle.kts", "gradle/libs.versions.toml"),
            ).shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { IngenAndreHttpKlienter.assertIngenKlienterIProduksjonskode(produksjonsscope) }
        feil.message shouldContain "HTTP-kall går via libs sin httpklient"

        val testfeil = shouldThrow<AssertionError> { IngenAndreHttpKlienter.assertIngenKlienterITestkode(testscope) }
        testfeil.message shouldContain "FakeHttpTransport"

        val byggfeil = shouldThrow<AssertionError> { IngenAndreHttpKlienter.assertIngenKlientavhengigheter(fixturesti("byggfiler")) }
        byggfeil.message shouldContain "Byggfilene skal ikke deklarere andre HTTP-klienter"
    }
}
