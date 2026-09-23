package no.nav.tiltakspenger.libs.httpklient.infra

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.httpklient.infra.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class HttpKlientConfigTest {
    @Test
    fun `HttpKlientConfig har trygge defaults`() {
        val config = HttpKlientConfig()

        config.timeout shouldBe 30.seconds
        config.auth shouldBe KlientAuth.Ingen
        config.retry shouldBe Retry.Ingen
        config.circuitBreaker shouldBe CircuitBreakerConfig.None
        config.skipCacheRetryStatuses shouldBe setOf(401)
        config.copy(skipCacheRetryStatuses = emptySet()).skipCacheRetryStatuses shouldBe emptySet()
    }
}
