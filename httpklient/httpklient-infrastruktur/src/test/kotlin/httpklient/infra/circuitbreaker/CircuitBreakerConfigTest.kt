package no.nav.tiltakspenger.libs.httpklient.infra.circuitbreaker

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import no.nav.tiltakspenger.libs.httpklient.infra.tomMetadata
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

internal class CircuitBreakerConfigTest {
    @Test
    fun `slidingWindow lagrer en tidskilde-fri strategi med brukervendt maxFailures`() {
        val config = CircuitBreakerConfig.slidingWindow(
            name = "test",
            maxFailures = 3,
            windowDuration = 10.seconds,
            resetTimeout = 5.seconds,
        )

        val slidingWindow = config.openingStrategy.shouldBeInstanceOf<CircuitBreakerOpeningStrategy.SlidingWindow>()
        slidingWindow.maxFailures shouldBe 3
        slidingWindow.windowDuration shouldBe 10.seconds
    }

    @Test
    fun `count lagrer en tidskilde-fri strategi med brukervendt maxFailures`() {
        val config = CircuitBreakerConfig.count(
            name = "test",
            maxFailures = 3,
            resetTimeout = 5.seconds,
        )

        config.openingStrategy.shouldBeInstanceOf<CircuitBreakerOpeningStrategy.Count>().maxFailures shouldBe 3
    }

    @Test
    fun `withTimeSource bytter kun tidskilde og lar strategien stå uendret`() {
        val customTimeSource: TimeSource = TestTimeSource()
        val config = CircuitBreakerConfig.slidingWindow(
            name = "test",
            maxFailures = 3,
            windowDuration = 10.seconds,
            resetTimeout = 5.seconds,
        ).withTimeSource(customTimeSource)

        config.timeSource shouldBe customTimeSource
        // Strategien er tidskilde-fri, så den er uberørt av byttet — det finnes kun én tidskilde.
        config.openingStrategy shouldBe CircuitBreakerOpeningStrategy.SlidingWindow(maxFailures = 3, windowDuration = 10.seconds)
    }

    @Test
    fun `Enabled validerer Count-strategien direkte i init`() {
        // Enabled er public, så en direkte konstruksjon (her via copy) med ugyldig maxFailures må avvises i init, ikke bare i factory-metodene.
        val gyldig = CircuitBreakerConfig.count(name = "test", maxFailures = 2, resetTimeout = 5.seconds)

        shouldThrowWithMessage<IllegalArgumentException>("maxFailures må være positiv, var 0") {
            gyldig.copy(openingStrategy = CircuitBreakerOpeningStrategy.Count(maxFailures = 0))
        }
    }

    @Test
    fun `Enabled validerer SlidingWindow-strategien direkte i init`() {
        val gyldig = CircuitBreakerConfig.slidingWindow(name = "test", maxFailures = 2, windowDuration = 10.seconds, resetTimeout = 5.seconds)

        shouldThrowWithMessage<IllegalArgumentException>("maxFailures må være positiv, var 0") {
            gyldig.copy(openingStrategy = CircuitBreakerOpeningStrategy.SlidingWindow(maxFailures = 0, windowDuration = 10.seconds))
        }
        shouldThrowWithMessage<IllegalArgumentException>("windowDuration må være positiv, var 0s") {
            gyldig.copy(openingStrategy = CircuitBreakerOpeningStrategy.SlidingWindow(maxFailures = 2, windowDuration = ZERO))
        }
    }

    @Test
    fun `Enabled validerer navn, resetTimeout og backoff i init`() {
        val gyldig = CircuitBreakerConfig.count("nedstrøms", maxFailures = 2, resetTimeout = 1.seconds)

        shouldThrowWithMessage<IllegalArgumentException>("name kan ikke være blank") {
            gyldig.copy(name = " ")
        }
        shouldThrowWithMessage<IllegalArgumentException>("resetTimeout må være positiv, var 0s") {
            gyldig.copy(resetTimeout = ZERO)
        }
        shouldThrowWithMessage<IllegalArgumentException>("exponentialBackoffFactor må være >= 1.0, var 0.5") {
            gyldig.copy(exponentialBackoffFactor = 0.5)
        }
        shouldThrowWithMessage<IllegalArgumentException>("maxResetTimeout (1s) må være >= resetTimeout (2s)") {
            gyldig.copy(resetTimeout = 2.seconds, maxResetTimeout = 1.seconds)
        }
    }

    @Test
    fun `fluent-metodene bytter kun sitt felt`() {
        val gyldig = CircuitBreakerConfig.count("nedstrøms", maxFailures = 2, resetTimeout = 1.seconds)
        val predikat: CircuitBreakerPredicate = { false }
        val callback: suspend () -> Unit = {}

        gyldig.withFailurePredicate(predikat).failurePredicate shouldBe predikat
        gyldig.withExponentialBackoff(factor = 2.0, maxResetTimeout = 30.seconds).exponentialBackoffFactor shouldBe 2.0
        gyldig.withExponentialBackoff(factor = 3.0).maxResetTimeout shouldBe gyldig.maxResetTimeout
        gyldig.doOnRejectedTask(callback).onRejected shouldBe callback
        gyldig.doOnClosed(callback).onClosed shouldBe callback
        gyldig.doOnHalfOpen(callback).onHalfOpen shouldBe callback
        gyldig.doOnOpen(callback).onOpen shouldBe callback
    }

    @Test
    fun `noop-callbackene fra fabrikkene kan kalles`() = runTest {
        // Default-callbackene er felles noop-lambdaer; kjør dem så den tomme kroppen også er verifisert kjørbar.
        val gyldig = CircuitBreakerConfig.count("nedstrøms", maxFailures = 2, resetTimeout = 1.seconds)
        gyldig.onRejected()
        gyldig.onClosed()
        gyldig.onHalfOpen()
        gyldig.onOpen()
    }

    @Test
    fun `DecisionContext bærer metode, uri og feil, og default-predikatene svarer på retryability`() {
        val feil = HttpKlientError.UventetStatus(503, "", tomMetadata(statusCode = 503))
        val ctx = CircuitBreakerDecisionContext(method = HttpMethod.GET, uri = URI.create("http://nedstrøms"), error = feil)

        ctx.method shouldBe HttpMethod.GET
        ctx.error shouldBe feil
        CircuitBreakerOnRetryableErrors(ctx) shouldBe true
        NeverRecordCircuitBreakerFailure(ctx) shouldBe false
        @Suppress("USELESS_IS_CHECK")
        (CircuitBreakerConfig.None is CircuitBreakerConfig) shouldBe true
    }
}
