package no.nav.tiltakspenger.libs.httpklient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientAuthTokenTest {
    @Test
    fun `bearer-token sendes ekte til serveren men maskeres i metadata`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/redaksjon")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/redaksjon")) {
                bearerToken(testAccessToken("hemmelig-token"))
            }.getOrFail()

            // Serveren mottar det ekte tokenet.
            wiremock.verify(getRequestedFor(urlEqualTo("/redaksjon")).withHeader("Authorization", equalTo("Bearer hemmelig-token")))
            // Men rawRequestString (som ofte logges) maskerer det.
            response.metadata.rawRequestString.shouldNotContain("hemmelig-token")
            response.metadata.rawRequestString.shouldContain("Authorization: ***")
        }
    }

    @Test
    fun `authTokenProvider gir Bearer-header på hver request`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/a")).willReturn(aResponse().withStatus(200).withBody("ok")))
            var calls = 0
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = authTokenProvider {
                    calls++
                    testAccessToken("tok-$calls")
                }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/a")).getOrFail()
            klient.get<String>(URI.create("${wiremock.baseUrl()}/a")).getOrFail()

            wiremock.verify(getRequestedFor(urlEqualTo("/a")).withHeader("Authorization", equalTo("Bearer tok-1")))
            wiremock.verify(getRequestedFor(urlEqualTo("/a")).withHeader("Authorization", equalTo("Bearer tok-2")))
            calls shouldBe 2
        }
    }

    @Test
    fun `per-request bearerToken overstyrer klient-default`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/b")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = authTokenProvider { error("provider skal ikke kalles når request setter token") }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/b")) {
                bearerToken(testAccessToken("override"))
            }.getOrFail()

            wiremock.verify(getRequestedFor(urlEqualTo("/b")).withHeader("Authorization", equalTo("Bearer override")))
        }
    }

    @Test
    fun `eksplisitt Authorization-header beholdes uendret`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/c")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = authTokenProvider { error("provider skal ikke kalles når Authorization er satt") }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/c")) {
                header("Authorization", "Basic abc")
            }.getOrFail()

            wiremock.verify(getRequestedFor(urlEqualTo("/c")).withHeader("Authorization", equalTo("Basic abc")))
        }
    }

    @Test
    fun `ingen authTokenProvider gir ingen Authorization-header`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/d")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            klient.get<String>(URI.create("${wiremock.baseUrl()}/d")).getOrFail()

            // wiremock vil reportere request uten Authorization-header — sjekk at ingen var sendt.
            wiremock.findAll(getRequestedFor(urlEqualTo("/d"))).single().header("Authorization").isPresent shouldBe false
        }
    }

    @Test
    fun `authTokenProvider som kaster gir AuthError uten HTTP-kall`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/e")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = authTokenProvider { throw IllegalStateException("token-endepunkt nede") }
            }

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/e")).swap().getOrNull()!!

            val authError = error.shouldBeInstanceOf<HttpKlientError.AuthError>()
            authError.throwable.message shouldBe "token-endepunkt nede"
            authError.retryable shouldBe false
            authError.metadata.attempts shouldBe 0
            wiremock.findAll(getRequestedFor(urlEqualTo("/e"))).size shouldBe 0
        }
    }

    @Test
    fun `401 utløser ett nytt forsøk der provider kalles med skipCache=true`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/skipcache"))
                    .inScenario("skip-cache")
                    .whenScenarioStateIs(STARTED)
                    .willReturn(aResponse().withStatus(401))
                    .willSetStateTo("andre-forsok"),
            )
            wiremock.stubFor(
                get(urlEqualTo("/skipcache"))
                    .inScenario("skip-cache")
                    .whenScenarioStateIs("andre-forsok")
                    .willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val skipCacheArgs = mutableListOf<Boolean>()
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("tok-skip=$skipCache")
                }
            }

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/skipcache")).getOrFail()

            response.body shouldBe "ok"
            skipCacheArgs shouldBe listOf(false, true)
            wiremock.verify(2, getRequestedFor(urlEqualTo("/skipcache")))
            wiremock.verify(getRequestedFor(urlEqualTo("/skipcache")).withHeader("Authorization", equalTo("Bearer tok-skip=true")))
        }
    }

    @Test
    fun `403 er ikke med i default skipCacheRetryStatuses og gir ingen retry`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/forbidden")).willReturn(aResponse().withStatus(403)))
            val skipCacheArgs = mutableListOf<Boolean>()
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                }
            }

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/forbidden")).swap().getOrNull()!!

            error.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 403
            skipCacheArgs shouldBe listOf(false)
            wiremock.verify(1, getRequestedFor(urlEqualTo("/forbidden")))
        }
    }

    @Test
    fun `403 kan opt-es inn i skipCacheRetryStatuses`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/forbidden-opt-in"))
                    .inScenario("forbidden")
                    .whenScenarioStateIs(STARTED)
                    .willReturn(aResponse().withStatus(403))
                    .willSetStateTo("andre-forsok"),
            )
            wiremock.stubFor(
                get(urlEqualTo("/forbidden-opt-in"))
                    .inScenario("forbidden")
                    .whenScenarioStateIs("andre-forsok")
                    .willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val skipCacheArgs = mutableListOf<Boolean>()
            val klient = HttpKlient(clock = fixedClock) {
                skipCacheRetryStatuses = setOf(401, 403)
                authTokenProvider = authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/forbidden-opt-in")).getOrFail().body shouldBe "ok"
            skipCacheArgs shouldBe listOf(false, true)
        }
    }

    @Test
    fun `tom skipCacheRetryStatuses slår av skip-cache-retry`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/av")).willReturn(aResponse().withStatus(401)))
            val skipCacheArgs = mutableListOf<Boolean>()
            val klient = HttpKlient(clock = fixedClock) {
                skipCacheRetryStatuses = emptySet()
                authTokenProvider = authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/av")).swap().getOrNull()!!

            skipCacheArgs shouldBe listOf(false)
            wiremock.verify(1, getRequestedFor(urlEqualTo("/av")))
        }
    }

    @Test
    fun `suksess på første forsøk gir ingen skip-cache-retry`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/ok")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val skipCacheArgs = mutableListOf<Boolean>()
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/ok")).getOrFail()

            skipCacheArgs shouldBe listOf(false)
            wiremock.verify(1, getRequestedFor(urlEqualTo("/ok")))
        }
    }

    @Test
    fun `vedvarende 401 gjør kun ett ekstra forsøk`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/alltid401")).willReturn(aResponse().withStatus(401)))
            val skipCacheArgs = mutableListOf<Boolean>()
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                }
            }

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/alltid401")).swap().getOrNull()!!

            error.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 401
            skipCacheArgs shouldBe listOf(false, true)
            wiremock.verify(2, getRequestedFor(urlEqualTo("/alltid401")))
        }
    }

    @Test
    fun `custom successStatus som godtar 401 gir ingen skip-cache-retry`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/godtatt401")).willReturn(aResponse().withStatus(401).withBody("ok")))
            val skipCacheArgs = mutableListOf<Boolean>()
            val klient = HttpKlient(clock = fixedClock) {
                successStatus = { it == 401 }
                authTokenProvider = authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/godtatt401")).getOrFail().body shouldBe "ok"

            skipCacheArgs shouldBe listOf(false)
            wiremock.verify(1, getRequestedFor(urlEqualTo("/godtatt401")))
        }
    }

    @Test
    fun `vedvarende 401 etter skip-cache-retry logges via loggingConfig (default WARN)`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/alltid401-logg")).willReturn(aResponse().withStatus(401)))
            val logger = testLogger()
            val klient = HttpKlient(clock = fixedClock) {
                logging = HttpKlientLoggingConfig(logger = logger, loggTilSikkerlogg = true)
                authTokenProvider = authTokenProvider { testAccessToken("t") }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/alltid401-logg")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.UventetStatus>()

            // Diagnostikkloggen for «ferskt token ble også avvist» går nå via loggingConfig, på skipCacheRetryNivå (default WARN).
            val meldinger = mutableListOf<() -> Any?>()
            verify(exactly = 1) { logger.warn(capture(meldinger)) }
            meldinger.single()().toString().shouldContain("skip-cache-retry")
        }
    }

    @Test
    fun `skipCacheRetryNivå OFF slår av skip-cache-diagnostikkloggen uten å påvirke andre kategorier`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/alltid401-av")).willReturn(aResponse().withStatus(401)))
            val logger = testLogger()
            val klient = HttpKlient(clock = fixedClock) {
                logging = HttpKlientLoggingConfig(logger = logger, skipCacheRetryNivå = HttpKlientLogNivå.OFF)
                authTokenProvider = authTokenProvider { testAccessToken("t") }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/alltid401-av")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.UventetStatus>()

            verify(exactly = 0) { logger.warn(any<() -> Any?>()) }
            // Selve 401-responsen logges fortsatt via klientfeil-kategorien, én gang per forsøk (2 forsøk).
            verify(exactly = 2) { logger.error(any<() -> Any?>()) }
        }
    }
}
