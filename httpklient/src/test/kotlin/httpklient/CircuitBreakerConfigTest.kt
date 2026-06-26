package no.nav.tiltakspenger.libs.httpklient

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerOpeningStrategy
import org.junit.jupiter.api.Test
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
}
