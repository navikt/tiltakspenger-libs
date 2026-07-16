package no.nav.tiltakspenger.libs.httpklient

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * Suksess-responsens domenesemantikk: invarianter, tryMap og sikkerlogg-hjelperen.
 * Hvordan pipelinen PRODUSERER responsene testes i infrastruktur-modulen.
 */
internal class HttpKlientResponseTest {
    private val respons = HttpKlientResponse(
        statusCode = 200,
        body = "råtekst",
        metadata = tomMetadata(rawRequestString = "GET http://x", rawResponseString = """{"a":1}""", statusCode = 200),
    )

    @Test
    fun `statusCode må være tresifret`() {
        shouldThrowWithMessage<IllegalArgumentException>("statusCode må være en tresifret HTTP-statuskode") {
            HttpKlientResponse(statusCode = 99, body = "x", metadata = tomMetadata())
        }
        HttpKlientResponse(statusCode = 999, body = "x", metadata = tomMetadata(rawResponseString = "")).statusCode shouldBe 999
    }

    @Test
    fun `rawResponseString er garantert non-null på suksess og feiler høyt ved invariant-brudd`() {
        respons.rawResponseString shouldBe """{"a":1}"""
        respons.rawRequestString shouldBe "GET http://x"

        val håndbygd = HttpKlientResponse(statusCode = 200, body = "x", metadata = tomMetadata(rawResponseString = null))
        shouldThrowWithMessage<IllegalStateException>("Invariant brutt: rawResponseString skal alltid være satt på en suksess-respons") {
            håndbygd.rawResponseString
        }
    }

    @Test
    fun `tryMap mapper suksess og fanger mapping-feil som DeserializationError med responsens metadata`() {
        respons.tryMap { it.length } shouldBe arrow.core.Either.Right(7)

        val feil = respons.tryMap { error("mapping feilet") }.swap().getOrNull()!!
        feil.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
        feil.statusCode shouldBe 200
        feil.body shouldBe """{"a":1}"""
    }

    @Test
    fun `loggTilSikkerlogg bruker invariant-aksessorene`() {
        // Røyktest på at hjelperen kan kalles på en gyldig respons; innholdet verifiseres ikke (Sikkerlogg er et objekt uten søm).
        respons.loggTilSikkerlogg("Sendte til datadeling.")
    }
}
