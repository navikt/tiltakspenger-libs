package no.nav.tiltakspenger.libs.httpklient.infra.retry

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Retry-datamodellens invarianter; mappingen til retry-motoren testes i infrastruktur-modulen.
 */
internal class RetryTest {

    @Test
    fun `Fast krever minst ett forsøk og ikke-negativ delay`() {
        shouldThrowWithMessage<IllegalArgumentException>("maksForsøk må være minst 1, var 0") {
            Retry.Fast(maksForsøk = 0)
        }
        shouldThrowWithMessage<IllegalArgumentException>("delay kan ikke være negativ, var -1s") {
            Retry.Fast(delay = (-1).seconds)
        }
        Retry.Fast() shouldBe Retry.Fast(maksForsøk = 3, delay = 1.seconds, retryIkkeIdempotente = false)
    }

    @Test
    fun `Standard krever konsistente delays`() {
        shouldThrowWithMessage<IllegalArgumentException>("maksForsøk må være minst 1, var 0") {
            Retry.Standard(maksForsøk = 0)
        }
        shouldThrowWithMessage<IllegalArgumentException>("grunnDelay kan ikke være negativ, var -1s") {
            Retry.Standard(grunnDelay = (-1).seconds)
        }
        shouldThrowWithMessage<IllegalArgumentException>("maksDelay (100ms) må være >= grunnDelay (250ms)") {
            Retry.Standard(maksDelay = 100.milliseconds)
        }
        Retry.Standard() shouldBe Retry.Standard(maksForsøk = 3, grunnDelay = 250.milliseconds, maksDelay = 2.seconds, retryIkkeIdempotente = false)
    }

    @Test
    fun `Ingen er et rent objekt`() {
        @Suppress("USELESS_IS_CHECK")
        (Retry.Ingen is Retry) shouldBe true
    }
}
