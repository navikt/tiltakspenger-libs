package no.nav.tiltakspenger.libs.lokal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class AvbruddTest {
    @Test
    fun `ProsessKommandokjører gir Avbrutt og setter interrupt-flagget igjen når tråden avbrytes`() {
        Thread.currentThread().interrupt()
        try {
            val feil = ProsessKommandokjører().kjør(listOf("sleep", "2"), arbeidskatalog = null, timeout = 10.seconds).feilen()

            feil shouldBe Kommandofeil.Avbrutt
            Thread.currentThread().isInterrupted shouldBe true
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `ProsessKommandokjører gir KunneIkkeStarte når kommandoen ikke finnes`() {
        val feil = ProsessKommandokjører().kjør(listOf("finnes-ikke-denne-kommandoen"), arbeidskatalog = null, timeout = 10.seconds).feilen()

        (feil is Kommandofeil.KunneIkkeStarte) shouldBe true
    }

    @Test
    fun `TrådsøvnVenting gir Avbrutt og setter interrupt-flagget igjen når tråden avbrytes`() {
        Thread.currentThread().interrupt()
        try {
            val feil = TrådsøvnVenting().vent(5.seconds, "steg").feilen()

            feil shouldBe LokalPostgresFeil.Avbrutt("steg")
            Thread.currentThread().isInterrupted shouldBe true
        } finally {
            Thread.interrupted()
        }
    }
}
