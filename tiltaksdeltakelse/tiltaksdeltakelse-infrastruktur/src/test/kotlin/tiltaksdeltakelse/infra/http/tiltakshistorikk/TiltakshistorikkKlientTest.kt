package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk

import arrow.core.nonEmptyListOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.testTokenProvider
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Dto
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.http.HttpTimeoutException
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class TiltakshistorikkKlientTest {
    private val baseUrl = "http://tiltakshistorikk.test"
    private val fnr = Fnr.fromString("12345678901")
    private val correlationId = CorrelationId("test-kall-id")

    private fun klient(transport: FakeHttpTransport) = TiltakshistorikkKlient(
        baseUrl = baseUrl,
        clock = fixedClock,
        authTokenProvider = testTokenProvider,
        transport = transport,
    )

    /**
     * JSON-en er kontraktens wire-format, med feltene kopien bevisst utelater (`organisasjonsnummer`, `gjennomforing.navn`) til stede.
     */
    private fun responsJson(deltakelseId: UUID, gjennomføringId: UUID) = """
        {
          "historikk": [
            {
              "type": "TeamKometDeltakelse",
              "norskIdent": "12345678901",
              "startDato": "2024-03-04",
              "sluttDato": null,
              "id": "$deltakelseId",
              "tittel": "Arbeidsforberedende trening hos Arrangør AS",
              "status": { "type": "DELTAR", "aarsak": null, "opprettetDato": "2024-03-01T09:30:00" },
              "tiltakstype": { "tiltakskode": "ARBEIDSFORBEREDENDE_TRENING", "navn": "Arbeidsforberedende trening" },
              "gjennomforing": { "id": "$gjennomføringId", "navn": "AFT 2024", "deltidsprosent": null },
              "arrangor": {
                "hovedenhet": null,
                "underenhet": { "organisasjonsnummer": "912345678", "navn": "Arrangør AS" }
              },
              "deltidsprosent": 60.0,
              "dagerPerUke": null
            }
          ],
          "meldinger": []
        }
    """.trimIndent()

    @Test
    fun `bygger default HttpKlient-oppsett når transport ikke sendes inn`() {
        TiltakshistorikkKlient(
            baseUrl = baseUrl,
            clock = fixedClock,
            authTokenProvider = testTokenProvider,
        )
    }

    @Test
    fun `alle konstruktørparametre kan overstyres`() {
        TiltakshistorikkKlient(
            baseUrl = baseUrl,
            clock = fixedClock,
            authTokenProvider = testTokenProvider,
            connectTimeout = 1.seconds,
            timeout = 5.seconds,
            transport = JavaHttpTransport(connectTimeout = 1.seconds),
        )
    }

    @Test
    fun `henter historikken, sender identene og kall-id`() = runTest {
        val deltakelseId = UUID.fromString("0190c9a2-1111-7000-8000-000000000001")
        val gjennomføringId = UUID.fromString("0190c9a2-2222-7000-8000-000000000002")
        val transport = FakeHttpTransport().apply { leggIKøJson(responsJson(deltakelseId, gjennomføringId)) }

        val respons = klient(transport).hentTiltakshistorikk(nonEmptyListOf(fnr), correlationId).getOrFail().body

        respons.historikk.map { (it as TiltakshistorikkV1Dto.TeamKometDeltakelse).id } shouldBe listOf(deltakelseId)
        respons.historikk.map { (it as TiltakshistorikkV1Dto.TeamKometDeltakelse).startDato } shouldBe listOf(LocalDate.of(2024, 3, 4))
        respons.meldinger shouldBe emptySet()

        val kall = transport.mottatteKall.single()
        kall.metode shouldBe "POST"
        kall.uri.toString() shouldBe "$baseUrl/api/v1/historikk"
        kall.bodyTekst shouldBe """{"identer":["12345678901"]}"""
        kall.request.headers().firstValue("Authorization").get() shouldBe "Bearer token"
        kall.request.headers().firstValue("Nav-Call-Id").get() shouldBe "test-kall-id"
    }

    @Test
    fun `andre 2xx enn 200 godtas ikke`() = runTest {
        val transport = FakeHttpTransport().apply { leggIKøStatus(statusCode = 202, body = """{"historikk":[],"meldinger":[]}""") }

        val feil = klient(transport).hentTiltakshistorikk(nonEmptyListOf(fnr), correlationId).leftOrNull().shouldNotBeNull()

        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 202
        // 202 er ikke en retryable status, så det gjøres bare ett forsøk.
        transport.mottatteKall.size shouldBe 1
    }

    @Test
    fun `serverfeil retryes tre ganger før den gir Left`() = runTest {
        val transport = FakeHttpTransport().apply { leggIKøStatusForAlleForsøk(statusCode = 500, body = "kaboom", maksForsøk = 3) }

        val feil = klient(transport).hentTiltakshistorikk(nonEmptyListOf(fnr), correlationId).leftOrNull().shouldNotBeNull()

        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 500
        // Retry-budsjettet er tre forsøk fordi 3 × 7 s + backoff må få plass i tidsbudsjettet hentingen deler med PDL-oppslaget.
        transport.mottatteKall.size shouldBe 3
    }

    @Test
    fun `timeout retryes og gir Left`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøKastForAlleForsøk(HttpTimeoutException("simulert timeout"), maksForsøk = 3)
        }

        val feil = klient(transport).hentTiltakshistorikk(nonEmptyListOf(fnr), correlationId).leftOrNull().shouldNotBeNull()

        feil.shouldBeInstanceOf<HttpKlientError.Timeout>()
        transport.mottatteKall.size shouldBe 3
    }

    @Test
    fun `nettverksfeil gir Left`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøKastForAlleForsøk(IOException("simulert nettverksfeil"), maksForsøk = 3)
        }

        val feil = klient(transport).hentTiltakshistorikk(nonEmptyListOf(fnr), correlationId).leftOrNull().shouldNotBeNull()

        feil.shouldBeInstanceOf<HttpKlientError.NetworkError>()
    }

    @Test
    fun `klientfeil retryes ikke`() = runTest {
        val transport = FakeHttpTransport().apply { leggIKøStatus(statusCode = 400, body = "nei") }

        val feil = klient(transport).hentTiltakshistorikk(nonEmptyListOf(fnr), correlationId).leftOrNull().shouldNotBeNull()

        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 400
        transport.mottatteKall.size shouldBe 1
    }
}
