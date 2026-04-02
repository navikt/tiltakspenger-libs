package no.nav.tiltakspenger.libs.kafka

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ConsumerStatusTest {
    @Test
    fun `retries - øker for hver failure`() {
        val status = ConsumerStatus()
        val feil = 42

        repeat(42) { status.failure() }

        status.retries shouldBe feil
        status.isFailure shouldBe true
    }

    @Test
    fun `retries - resettes til 0 etter success`() {
        val status = ConsumerStatus()
        val feil = 7

        repeat(7) { status.failure() }

        status.retries shouldBe feil
        status.isFailure shouldBe true

        status.success()

        status.retries shouldBe 0
        status.isFailure shouldBe false
    }

    @Test
    fun `backoffDuration - skal aldri være større enn MAX_DELAY`() {
        val status = ConsumerStatus()

        repeat(25) { status.failure() }

        status.backoffDuration shouldBe ConsumerStatus.MAX_DELAY

        status.failure()

        status.backoffDuration shouldBe ConsumerStatus.MAX_DELAY
    }
}
