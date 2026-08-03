package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikkmelding
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakskilde
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.UkjentDeltakelsesform
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl.PdlIdentklient
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.testTokenProvider
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkKlient
import org.junit.jupiter.api.Test

class TiltakshistorikkHenterTest {
    private val fnr = Fnr.fromString("12345678901")
    private val historiskFnr = Fnr.fromString("10987654321")
    private val correlationId = CorrelationId("test-kall-id")

    private fun henter(
        pdlTransport: FakeHttpTransport,
        historikkTransport: FakeHttpTransport,
    ) = TiltakshistorikkHenter(
        tiltakshistorikkKlient = TiltakshistorikkKlient(
            baseUrl = "http://tiltakshistorikk.test",
            clock = fixedClock,
            authTokenProvider = testTokenProvider,
            transport = historikkTransport,
        ),
        pdlIdentklient = PdlIdentklient(
            baseUrl = "http://pdl.test",
            clock = fixedClock,
            authTokenProvider = testTokenProvider,
            transport = pdlTransport,
        ),
        clock = fixedClock,
    )

    private fun pdlJson(vararg identer: Fnr) = """
        {"data": {"hentIdenter": {"identer": [${identer.joinToString(",") { """{"ident": "${it.verdi}"}""" }}]}}, "errors": null}
    """.trimIndent()

    private fun arenaRadJson(ident: Fnr = fnr, arenaId: Int = 142536, status: String = "GJENNOMFORES", tiltakskode: String = "INDOPPFAG") = """
        {
          "type": "ArenaDeltakelse",
          "norskIdent": "${ident.verdi}",
          "startDato": "2024-01-01",
          "sluttDato": "2024-06-30",
          "id": "019018e5-6461-74a0-9d66-70d0bf3d0b8b",
          "tittel": "Oppfølging hos Arrangør AS",
          "arenaId": $arenaId,
          "status": "$status",
          "tiltakstype": { "tiltakskode": "$tiltakskode", "navn": "Oppfølging" },
          "gjennomforing": { "id": "0190c9a2-1111-7000-8000-000000000001", "navn": null, "deltidsprosent": 50.0 },
          "arrangor": { "hovedenhet": null, "underenhet": { "organisasjonsnummer": "912345678", "navn": "Arrangør AS" } },
          "deltidsprosent": 100.0,
          "dagerPerUke": 5.0
        }
    """.trimIndent()

    private fun kometRadJson(ident: Fnr = fnr) = """
        {
          "type": "TeamKometDeltakelse",
          "norskIdent": "${ident.verdi}",
          "startDato": "2024-03-04",
          "sluttDato": null,
          "id": "0190c9a2-2222-7000-8000-000000000002",
          "tittel": "Arbeidsforberedende trening hos Arrangør AS",
          "status": { "type": "DELTAR", "aarsak": null, "opprettetDato": "2024-03-01T09:30:00" },
          "tiltakstype": { "tiltakskode": "ARBEIDSFORBEREDENDE_TRENING", "navn": "Arbeidsforberedende trening" },
          "gjennomforing": { "id": "0190c9a2-3333-7000-8000-000000000003", "navn": null, "deltidsprosent": null },
          "arrangor": { "hovedenhet": null, "underenhet": { "organisasjonsnummer": "912345678", "navn": null } },
          "deltidsprosent": 60.0,
          "dagerPerUke": null
        }
    """.trimIndent()

    private fun teamTiltakRadJson(ident: Fnr = fnr) = """
        {
          "type": "TeamTiltakAvtale",
          "norskIdent": "${ident.verdi}",
          "startDato": "2025-01-01",
          "sluttDato": null,
          "id": "0190c9a2-4444-7000-8000-000000000004",
          "tittel": "Arbeidstrening hos Butikken AS",
          "tiltakstype": { "tiltakskode": "ARBEIDSTRENING", "navn": "Arbeidstrening" },
          "status": "GJENNOMFORES",
          "stillingsprosent": 50.0,
          "dagerPerUke": 4.0,
          "arbeidsgiver": { "organisasjonsnummer": "999888777", "navn": "Butikken AS" }
        }
    """.trimIndent()

    private fun responsJson(rader: List<String>, meldinger: List<String> = emptyList()) = """
        {"historikk": [${rader.joinToString(",")}], "meldinger": [${meldinger.joinToString(",") { "\"$it\"" }}]}
    """.trimIndent()

    @Test
    fun `henter historikken for alle identene og bygger hente-resultatet`() = runTest {
        val pdlTransport = FakeHttpTransport().apply { leggIKøJson(pdlJson(fnr, historiskFnr)) }
        val historikkTransport = FakeHttpTransport().apply {
            leggIKøJson(
                responsJson(
                    rader = listOf(
                        arenaRadJson(ident = fnr),
                        kometRadJson(ident = historiskFnr),
                        teamTiltakRadJson(ident = fnr),
                        """{ "type": "NyDeltakelsesform", "noe": 1 }""",
                    ),
                    meldinger = listOf("MANGLER_HISTORIKK_FRA_TEAM_TILTAK", "HELT_NY_MELDING"),
                ),
            )
        }

        val historikk = henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).getOrFail()

        historikk.deltakelser.deltakelser.map { it.id.verdi } shouldBe listOf(
            "TA142536",
            "0190c9a2-2222-7000-8000-000000000002",
            "0190c9a2-4444-7000-8000-000000000004",
        )
        historikk.meldinger.verdi shouldBe listOf(
            Tiltakshistorikkmelding.ManglerHistorikkFraTeamTiltak,
            Tiltakshistorikkmelding.Ukjent("HELT_NY_MELDING"),
        )
        historikk.meldinger.manglendeKilder shouldBe setOf(Tiltakskilde.TeamTiltak)
        historikk.ukjenteDeltakelsesformer.verdi shouldBe listOf(UkjentDeltakelsesform("NyDeltakelsesform"))
        historikk.hentetTidspunkt shouldBe nå(fixedClock)

        val kall = historikkTransport.mottatteKall.single()
        kall.bodyTekst shouldContain fnr.verdi
        kall.bodyTekst shouldContain historiskFnr.verdi
    }

    @Test
    fun `innsendt fnr legges til når PDL ikke returnerer det`() = runTest {
        val pdlTransport = FakeHttpTransport().apply { leggIKøJson(pdlJson(historiskFnr)) }
        val historikkTransport = FakeHttpTransport().apply { leggIKøJson(responsJson(emptyList())) }

        henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).getOrFail()

        val body = historikkTransport.mottatteKall.single().bodyTekst
        body shouldContain historiskFnr.verdi
        body shouldContain fnr.verdi
    }

    @Test
    fun `feiler identoppslaget på kall-nivå, feiler hentingen uten å kalle tiltakshistorikk`() = runTest {
        val pdlTransport = FakeHttpTransport().apply { leggIKøStatus(statusCode = 500, body = "kaboom") }
        val historikkTransport = FakeHttpTransport()

        val feil = henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).leftOrNull().shouldNotBeNull()

        feil shouldBe KunneIkkeHenteTiltakshistorikk.IdentoppslagFeilet
        historikkTransport.mottatteKall.shouldBeEmpty()
    }

    @Test
    fun `graphql-feil i identoppslaget faller tilbake til innsendt fnr`() = runTest {
        val pdlTransport = FakeHttpTransport().apply {
            leggIKøJson("""{"data": null, "errors": [{"message": "feil", "locations": null, "path": null, "extensions": null}]}""")
        }
        val historikkTransport = FakeHttpTransport().apply { leggIKøJson(responsJson(emptyList())) }

        henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).getOrFail()

        val body = historikkTransport.mottatteKall.single().bodyTekst
        body shouldContain fnr.verdi
        body shouldNotContain historiskFnr.verdi
    }

    @Test
    fun `tomt identsvar faller tilbake til innsendt fnr`() = runTest {
        val pdlTransport = FakeHttpTransport().apply {
            leggIKøJson("""{"data": {"hentIdenter": {"identer": []}}, "errors": null}""")
        }
        val historikkTransport = FakeHttpTransport().apply { leggIKøJson(responsJson(emptyList())) }

        henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).getOrFail()

        historikkTransport.mottatteKall.single().bodyTekst shouldContain fnr.verdi
    }

    @Test
    fun `ugyldig ident i identsvaret faller tilbake til innsendt fnr`() = runTest {
        val pdlTransport = FakeHttpTransport().apply {
            leggIKøJson("""{"data": {"hentIdenter": {"identer": [{"ident": "ikke-et-fnr"}]}}, "errors": null}""")
        }
        val historikkTransport = FakeHttpTransport().apply { leggIKøJson(responsJson(emptyList())) }

        henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).getOrFail()

        historikkTransport.mottatteKall.single().bodyTekst shouldContain fnr.verdi
    }

    @Test
    fun `feiler tiltakshistorikk-kallet, feiler hentingen`() = runTest {
        val pdlTransport = FakeHttpTransport().apply { leggIKøJson(pdlJson(fnr)) }
        val historikkTransport = FakeHttpTransport().apply { leggIKøStatusForAlleForsøk(statusCode = 500, body = "kaboom", maksForsøk = 3) }

        val feil = henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).leftOrNull().shouldNotBeNull()

        feil shouldBe KunneIkkeHenteTiltakshistorikk.KallFeilet
    }

    @Test
    fun `en blank kode i svaret feller hele hentingen som ugyldig respons`() = runTest {
        val pdlTransport = FakeHttpTransport().apply { leggIKøJson(pdlJson(fnr)) }
        val historikkTransport = FakeHttpTransport().apply {
            leggIKøJson(responsJson(rader = listOf(arenaRadJson(status = " "))))
        }

        henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).leftOrNull().shouldNotBeNull() shouldBe
            KunneIkkeHenteTiltakshistorikk.UgyldigRespons("Blank deltakerstatus fra Arena kan ikke bæres som ukjent kildeverdi")
    }

    @Test
    fun `en rad for en ident det ikke ble spurt om feller hentingen`() = runTest {
        val fremmedFnr = Fnr.fromString("11111111111")
        val pdlTransport = FakeHttpTransport().apply { leggIKøJson(pdlJson(fnr)) }
        val historikkTransport = FakeHttpTransport().apply {
            leggIKøJson(responsJson(rader = listOf(arenaRadJson(ident = fremmedFnr))))
        }

        henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).leftOrNull().shouldNotBeNull() shouldBe
            KunneIkkeHenteTiltakshistorikk.UgyldigRespons("Svaret inneholder en rad for en ident det ikke ble spurt om")
    }

    @Test
    fun `en deltakelse uten eller med blank type-diskriminator feller hentingen`() = runTest {
        listOf("""{ "noe": 1 }""", """{ "type": " " }""").forEach { rad ->
            val pdlTransport = FakeHttpTransport().apply { leggIKøJson(pdlJson(fnr)) }
            val historikkTransport = FakeHttpTransport().apply { leggIKøJson(responsJson(rader = listOf(rad))) }

            henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).leftOrNull().shouldNotBeNull() shouldBe
                KunneIkkeHenteTiltakshistorikk.UgyldigRespons("Svaret inneholder en deltakelse uten type-diskriminator")
        }
    }

    @Test
    fun `dupliserte deltakelses-ider feller hentingen`() = runTest {
        val pdlTransport = FakeHttpTransport().apply { leggIKøJson(pdlJson(fnr)) }
        val historikkTransport = FakeHttpTransport().apply {
            leggIKøJson(responsJson(rader = listOf(arenaRadJson(arenaId = 1), arenaRadJson(arenaId = 1))))
        }

        henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).leftOrNull().shouldNotBeNull() shouldBe
            KunneIkkeHenteTiltakshistorikk.UgyldigRespons("Svaret inneholder dupliserte deltakelses-ider")
    }

    @Test
    fun `en blank melding feller hentingen`() = runTest {
        val pdlTransport = FakeHttpTransport().apply { leggIKøJson(pdlJson(fnr)) }
        val historikkTransport = FakeHttpTransport().apply {
            leggIKøJson(responsJson(rader = emptyList(), meldinger = listOf(" ")))
        }

        henter(pdlTransport, historikkTransport).hentTiltakshistorikk(fnr, correlationId).leftOrNull().shouldNotBeNull() shouldBe
            KunneIkkeHenteTiltakshistorikk.UgyldigRespons("Blank melding fra tiltakshistorikk kan ikke bæres som ukjent kildeverdi")
    }
}
