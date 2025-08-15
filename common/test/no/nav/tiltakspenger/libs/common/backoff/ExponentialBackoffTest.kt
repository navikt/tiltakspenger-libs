package no.nav.tiltakspenger.libs.common.backoff

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.plus
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal class ExponentialBackoffTest {
    @Test
    fun `Skal prøve på nytt ved 0 forsøk`() {
        LocalDateTime.now().shouldRetry(0, fixedClock) shouldBe true
    }

    @Test
    fun `Skal ikke forsøke på nytt ved 1 forsøk og mindre enn 1 minutt har passert`() {
        val clockAfter = fixedClock.plus(59, ChronoUnit.SECONDS)
        LocalDateTime.now(fixedClock).shouldRetry(1, clockAfter) shouldBe false
    }

    @Test
    fun `Skal forsøke på nytt ved 1 forsøk og nøyaktig 1 minutt har passert`() {
        val clockAfter = fixedClock.plus(1, ChronoUnit.MINUTES)
        LocalDateTime.now(fixedClock).shouldRetry(1, clockAfter) shouldBe true
    }

    @Test
    fun `Skal forsøke på nytt ved 1 forsøk og mer enn 1 minutt har passert`() {
        val clockAfter = fixedClock.plus(1, ChronoUnit.DAYS)
        LocalDateTime.now(fixedClock).shouldRetry(1, clockAfter) shouldBe true
    }

    @Test
    fun `Skal ikke forsøke på nytt ved 2 forsøk og det mindre enn 5 minutt har passert`() {
        val clockAfter = fixedClock.plus(299, ChronoUnit.SECONDS)
        LocalDateTime.now(fixedClock).shouldRetry(2, clockAfter) shouldBe false
    }

    @Test
    fun `Skal forsøke på nytt ved 2 forsøk og nøyaktig 5 minutt har passert`() {
        val clockAfter = fixedClock.plus(5, ChronoUnit.MINUTES)
        LocalDateTime.now(fixedClock).shouldRetry(2, clockAfter) shouldBe true
    }

    @Test
    fun `Skal ikke forsøke på nytt om maks ventetid ikke har passert`() {
        val clockAfter = fixedClock
            .plus(23, ChronoUnit.HOURS)
            .plus(59, ChronoUnit.MINUTES)
        LocalDateTime.now(fixedClock).shouldRetry(11, clockAfter) shouldBe false
    }

    @Test
    fun `Skal forsøke på nytt om maks ventetid har passert`() {
        val clockAfter = fixedClock.plus(24, ChronoUnit.HOURS)
        LocalDateTime.now(fixedClock).shouldRetry(11, clockAfter) shouldBe true
    }
}
