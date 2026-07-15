package no.nav.tiltakspenger.libs.httpklient.infra.kall

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.AccessToken
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * De rene kall-parametertypene: Header/NavHeadere, Statusregel, SerialisertJson, KlientAuth og HttpMethod.
 */
internal class KallTyperTest {

    @Test
    fun `Header avviser reserverte navn uansett casing`() {
        listOf("Content-Type", "accept", "AUTHORIZATION", "Content-Length", "host").forEach { navn ->
            shouldThrowWithMessage<IllegalArgumentException>("Headeren '$navn' eies av HttpKlient og settes automatisk av metoden du kaller.") {
                Header(navn, "verdi")
            }
        }
    }

    @Test
    fun `Header avviser blankt navn og defaulter til ikke-sensitiv`() {
        shouldThrowWithMessage<IllegalArgumentException>("Headernavn kan ikke være blankt") {
            Header(" ", "verdi")
        }
        Header("X-Egen", "verdi").sensitiv.shouldBeFalse()
    }

    @Test
    fun `NavHeadere bruker de eksakte stavemåtene nedstrømstjenestene krever`() {
        NavHeadere.xCorrelationId("abc") shouldBe Header("X-Correlation-ID", "abc")
        NavHeadere.navCallId("abc") shouldBe Header("Nav-Call-Id", "abc")
        NavHeadere.navCallid("abc") shouldBe Header("Nav-Callid", "abc")
        NavHeadere.navConsumerId("app") shouldBe Header("Nav-Consumer-Id", "app")
        NavHeadere.tema("IND") shouldBe Header("Tema", "IND")
        NavHeadere.behandlingsnummer("B470") shouldBe Header("behandlingsnummer", "B470")
        NavHeadere.ident("12345678901") shouldBe Header("ident", "12345678901", sensitiv = true)
    }

    @Test
    fun `Statusregel Eksakt krever minst én tresifret statuskode`() {
        shouldThrowWithMessage<IllegalArgumentException>("Eksakt må ha minst én statuskode") {
            Statusregel.Eksakt(emptySet())
        }
        shouldThrowWithMessage<IllegalArgumentException>("Statuskode må være tresifret, var 99") {
            Statusregel.Eksakt(99)
        }
        Statusregel.Eksakt(200, 202).statuser shouldBe setOf(200, 202)
        Statusregel.Alle2xx shouldBe Statusregel.Alle2xx
    }

    @Test
    fun `SerialisertJson bærer strengen verbatim`() {
        SerialisertJson("""{"a":1}""").json shouldBe """{"a":1}"""
    }

    @Test
    fun `KlientAuth System bærer providern og Ingen er et rent objekt`() = runTest {
        val token = AccessToken("t", Instant.EPOCH)
        val provider = object : AuthTokenProvider {
            override suspend fun hentToken(skipCache: Boolean): AccessToken = if (skipCache) error("uventet") else token
        }

        (KlientAuth.System(provider).provider.hentToken(skipCache = false)) shouldBe token
        @Suppress("USELESS_IS_CHECK")
        (KlientAuth.Ingen is KlientAuth).shouldBeTrue()
    }

    @Test
    fun `HttpMethod inneholder kun metodene appene bruker`() {
        HttpMethod.entries.map { it.name } shouldBe listOf("GET", "POST", "PUT", "PATCH")
    }
}
