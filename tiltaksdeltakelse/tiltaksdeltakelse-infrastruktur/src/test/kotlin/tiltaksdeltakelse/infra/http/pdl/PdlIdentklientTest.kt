package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl

import arrow.core.nonEmptyListOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.testTokenProvider
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class PdlIdentklientTest {
    private val baseUrl = "http://pdl.test"
    private val fnr = Fnr.fromString("12345678901")
    private val historiskFnr = Fnr.fromString("10987654321")

    private fun klient(transport: FakeHttpTransport) = PdlIdentklient(
        baseUrl = baseUrl,
        clock = fixedClock,
        authTokenProvider = testTokenProvider,
        transport = transport,
    )

    //language=JSON
    private val happyJson = """
        {
          "data": {
            "hentIdenter": {
              "identer": [
                { "ident": "${fnr.verdi}" },
                { "ident": "${historiskFnr.verdi}" }
              ]
            }
          },
          "errors": null
        }
    """.trimIndent()

    @Test
    fun `bygger default HttpKlient-oppsett når transport ikke sendes inn`() {
        PdlIdentklient(
            baseUrl = baseUrl,
            clock = fixedClock,
            authTokenProvider = testTokenProvider,
        )
    }

    @Test
    fun `alle konstruktørparametre kan overstyres`() {
        PdlIdentklient(
            baseUrl = baseUrl,
            clock = fixedClock,
            authTokenProvider = testTokenProvider,
            connectTimeout = 1.seconds,
            timeout = 3.seconds,
            transport = JavaHttpTransport(connectTimeout = 1.seconds),
        )
    }

    @Test
    fun `henter nåværende og historiske identer og sender PDL-headerne`() = runTest {
        val transport = FakeHttpTransport().apply { leggIKøJson(happyJson) }

        val identer = klient(transport).hentNåværendeOgHistoriskeFnr(fnr).getOrFail()

        identer shouldBe nonEmptyListOf(fnr, historiskFnr)
        val kall = transport.mottatteKall.single()
        kall.metode shouldBe "POST"
        kall.uri.toString() shouldBe "$baseUrl/graphql"
        kall.request.headers().firstValue("Tema").get() shouldBe "IND"
        kall.request.headers().firstValue("behandlingsnummer").get() shouldBe "B470"
        kall.request.headers().firstValue("Authorization").get() shouldBe "Bearer token"
        kall.bodyTekst shouldContain fnr.verdi
        kall.bodyTekst shouldContain "hentIdenter"
    }

    @Test
    fun `godtar alle 2xx`() = runTest {
        val transport = FakeHttpTransport().apply { leggIKøJson(happyJson, statusCode = 201) }

        klient(transport).hentNåværendeOgHistoriskeFnr(fnr).getOrFail() shouldBe nonEmptyListOf(fnr, historiskFnr)
    }

    @Test
    fun `graphql-feil gir GraphQLFeil med feilmeldingene`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(
                """{"data": {"hentIdenter": null}, "errors": [{"message": "Fant ikke person", "locations": [{"line": "1", "column": "2"}], "path": ["hentIdenter"], "extensions": {"code": "not_found", "classification": null}}]}""",
            )
        }

        val feil = klient(transport).hentNåværendeOgHistoriskeFnr(fnr).leftOrNull().shouldNotBeNull()

        feil.shouldBeInstanceOf<KanIkkeHenteIdenter.UtenBrukbareIdenter.GraphQLFeil>().feilmeldinger shouldBe nonEmptyListOf("Fant ikke person")
    }

    @Test
    fun `graphql-feil uten melding rapporteres som ukjent`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson("""{"data": null, "errors": [{"message": null, "locations": null, "path": null, "extensions": null}]}""")
        }

        val feil = klient(transport).hentNåværendeOgHistoriskeFnr(fnr).leftOrNull().shouldNotBeNull()

        feil.shouldBeInstanceOf<KanIkkeHenteIdenter.UtenBrukbareIdenter.GraphQLFeil>().feilmeldinger shouldBe nonEmptyListOf("ukjent")
    }

    @Test
    fun `tom identliste gir FantIngenIdenter`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson("""{"data": {"hentIdenter": {"identer": []}}, "errors": null}""")
        }

        klient(transport).hentNåværendeOgHistoriskeFnr(fnr)
            .leftOrNull()
            .shouldNotBeNull()
            .shouldBeInstanceOf<KanIkkeHenteIdenter.UtenBrukbareIdenter.FantIngenIdenter>()
    }

    @Test
    fun `respons med data men uten identliste gir FantIngenIdenter`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson("""{"data": {"hentIdenter": null}, "errors": null}""")
        }

        klient(transport).hentNåværendeOgHistoriskeFnr(fnr)
            .leftOrNull()
            .shouldNotBeNull()
            .shouldBeInstanceOf<KanIkkeHenteIdenter.UtenBrukbareIdenter.FantIngenIdenter>()
    }

    @Test
    fun `respons uten data gir FantIngenIdenter`() = runTest {
        val transport = FakeHttpTransport().apply { leggIKøJson("""{"data": null, "errors": null}""") }

        klient(transport).hentNåværendeOgHistoriskeFnr(fnr)
            .leftOrNull()
            .shouldNotBeNull()
            .shouldBeInstanceOf<KanIkkeHenteIdenter.UtenBrukbareIdenter.FantIngenIdenter>()
    }

    @Test
    fun `en ident som ikke er et gyldig fødselsnummer gir UgyldigIdent`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson("""{"data": {"hentIdenter": {"identer": [{"ident": "ikke-et-fnr"}]}}, "errors": null}""")
        }

        klient(transport).hentNåværendeOgHistoriskeFnr(fnr)
            .leftOrNull()
            .shouldNotBeNull()
            .shouldBeInstanceOf<KanIkkeHenteIdenter.UtenBrukbareIdenter.UgyldigIdent>()
    }

    @Test
    fun `uventet status gir KallFeilet uten retry`() = runTest {
        val transport = FakeHttpTransport().apply { leggIKøStatus(statusCode = 500, body = "kaboom") }

        val feil = klient(transport).hentNåværendeOgHistoriskeFnr(fnr).leftOrNull().shouldNotBeNull()

        feil.shouldBeInstanceOf<KanIkkeHenteIdenter.KallFeilet>()
            .httpKlientError
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
            .statusCode shouldBe 500
        // Klienten har Retry.Ingen, som klienten den er portert fra.
        transport.mottatteKall.size shouldBe 1
    }

    @Test
    fun `nettverksfeil gir KallFeilet`() = runTest {
        val transport = FakeHttpTransport().apply { leggIKøKast(IOException("simulert nettverksfeil")) }

        val feil = klient(transport).hentNåværendeOgHistoriskeFnr(fnr).leftOrNull().shouldNotBeNull()

        feil.shouldBeInstanceOf<KanIkkeHenteIdenter.KallFeilet>()
            .httpKlientError
            .shouldBeInstanceOf<HttpKlientError.NetworkError>()
    }

    @Test
    fun `identen maskeres i toString på requesten`() {
        PdlVariables(ident = fnr.verdi).toString() shouldBe "PdlVariables(ident=*****)"
    }
}
