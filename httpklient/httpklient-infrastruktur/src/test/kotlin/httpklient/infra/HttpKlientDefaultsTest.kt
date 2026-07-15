package no.nav.tiltakspenger.libs.httpklient.infra

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.kall.godtar
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import org.junit.jupiter.api.Test

internal class HttpKlientDefaultsTest {
    @Test
    fun `kan opprette klient med default config og transport`() {
        HttpKlient(clock = fixedClock).shouldBeInstanceOf<HttpKlient>()
    }

    @Test
    fun `default config har Ingen auth, Ingen retry og ingen circuit breaker`() {
        val config = HttpKlientConfig()

        config.auth shouldBe KlientAuth.Ingen
        config.retry shouldBe Retry.Ingen
        config.skipCacheRetryStatuses shouldBe setOf(401)
    }

    @Test
    fun `Alle2xx godtar kun 200-299`() {
        Statusregel.Alle2xx.godtar(199) shouldBe false
        Statusregel.Alle2xx.godtar(200) shouldBe true
        Statusregel.Alle2xx.godtar(299) shouldBe true
        Statusregel.Alle2xx.godtar(300) shouldBe false
        Statusregel.Alle2xx.godtar(404) shouldBe false
        Statusregel.Alle2xx.godtar(500) shouldBe false
    }

    @Test
    fun `Eksakt godtar kun oppgitte koder`() {
        val regel = Statusregel.Eksakt(200, 201, 202)

        regel.godtar(200) shouldBe true
        regel.godtar(201) shouldBe true
        regel.godtar(202) shouldBe true
        regel.godtar(204) shouldBe false
        regel.godtar(404) shouldBe false
    }

    @Test
    fun `Eksakt kan sammenlignes som data - i motsetning til gamle predikater`() {
        Statusregel.Eksakt(200, 202) shouldBe Statusregel.Eksakt(setOf(202, 200))
    }

    @Test
    fun `Eksakt krever minst én kode`() {
        shouldThrowWithMessage<IllegalArgumentException>("Eksakt må ha minst én statuskode") {
            Statusregel.Eksakt(emptySet())
        }
    }

    @Test
    fun `Eksakt avviser statuskoder utenfor tresifret range`() {
        shouldThrowWithMessage<IllegalArgumentException>("Statuskode må være tresifret, var 99") {
            Statusregel.Eksakt(99)
        }
        shouldThrowWithMessage<IllegalArgumentException>("Statuskode må være tresifret, var 1000") {
            Statusregel.Eksakt(1000)
        }
    }
}
