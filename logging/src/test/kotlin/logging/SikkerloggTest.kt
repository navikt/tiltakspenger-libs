package no.nav.tiltakspenger.libs.logging

import io.github.oshai.kotlinlogging.Level
import io.kotest.matchers.booleans.shouldBeFalse
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import no.nav.tiltakspenger.libs.logging.infra.KotlinLoggingSikkerlogg
import org.junit.jupiter.api.Test

/**
 * Røyktester på at sikkerlogg kan kalles på alle nivåer uten å feile; loggeren er hardkodet, så innholdet kan ikke verifiseres.
 */
internal class SikkerloggTest {
    @Test
    fun `statiske kallsteder treffer companion-objektet på alle nivåer, med og uten throwable`() {
        val feil = RuntimeException("feil for test")
        Sikkerlogg.trace { "trace uten throwable" }
        Sikkerlogg.trace(feil) { "trace med throwable" }
        Sikkerlogg.debug { "debug uten throwable" }
        Sikkerlogg.debug(feil) { "debug med throwable" }
        Sikkerlogg.info { "info uten throwable" }
        Sikkerlogg.info(feil) { "info med throwable" }
        Sikkerlogg.warn { "warn uten throwable" }
        Sikkerlogg.warn(feil) { "warn med throwable" }
        Sikkerlogg.error { "error uten throwable" }
        Sikkerlogg.error(feil) { "error med throwable" }
    }

    @Test
    fun `KotlinLoggingSikkerlogg kan brukes som injisert instans på alle nivåer, med og uten throwable`() {
        loggPåAlleNivåer(KotlinLoggingSikkerlogg())
    }

    @Test
    fun `companion-objektet kan brukes som injisert instans på alle nivåer, med og uten throwable`() {
        loggPåAlleNivåer(Sikkerlogg)
    }

    @Test
    fun `logg sender meldingen til valgt nivå og ignorerer OFF`() {
        val sikkerlogg = mockk<Sikkerlogg>(relaxed = true)
        val feil = RuntimeException("feil for test")

        listOf(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR).forEach { level ->
            sikkerlogg.logg(level, feil) { "melding" }

            verify(exactly = 1) {
                when (level) {
                    Level.TRACE -> sikkerlogg.trace(feil, any())
                    Level.DEBUG -> sikkerlogg.debug(feil, any())
                    Level.INFO -> sikkerlogg.info(feil, any())
                    Level.WARN -> sikkerlogg.warn(feil, any())
                    Level.ERROR -> sikkerlogg.error(feil, any())
                    Level.OFF -> Unit
                }
            }
        }

        val avskruddSikkerlogg = mockk<Sikkerlogg>(relaxed = true)
        var meldingEvaluert = false
        avskruddSikkerlogg.logg(Level.OFF, feil) {
            meldingEvaluert = true
            "melding"
        }
        verify { avskruddSikkerlogg wasNot Called }
        meldingEvaluert.shouldBeFalse()
    }

    private fun loggPåAlleNivåer(sikkerlogg: Sikkerlogg) {
        val feil = RuntimeException("feil for test")
        sikkerlogg.trace { "trace uten throwable" }
        sikkerlogg.trace(feil) { "trace med throwable" }
        sikkerlogg.debug { "debug uten throwable" }
        sikkerlogg.debug(feil) { "debug med throwable" }
        sikkerlogg.info { "info uten throwable" }
        sikkerlogg.info(feil) { "info med throwable" }
        sikkerlogg.warn { "warn uten throwable" }
        sikkerlogg.warn(feil) { "warn med throwable" }
        sikkerlogg.error { "error uten throwable" }
        sikkerlogg.error(feil) { "error med throwable" }
    }
}
