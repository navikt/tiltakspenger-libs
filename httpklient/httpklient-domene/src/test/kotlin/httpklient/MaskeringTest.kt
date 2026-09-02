package no.nav.tiltakspenger.libs.httpklient

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.tiltakspenger.libs.common.FnrGenerator
import org.junit.jupiter.api.Test

/**
 * `toString()` er den stien ingen planlegger å bruke, men alle treffer: én `log.error { "$feil" }` hos en konsument.
 * Derfor pinnes maskeringen her, med de faktiske verdiene vi er redde for — et fødselsnummer og et bearer-token.
 */
internal class MaskeringTest {
    private val fnr = FnrGenerator().generer().verdi
    private val fnrIRequest = """{"ident": "$fnr"}"""
    private val personIRespons = """{"navn": "Ola Nordmann", "fnr": "$fnr"}"""

    private fun metadata() = tomMetadata(
        rawRequestString = fnrIRequest,
        rawResponseString = personIRespons,
        requestHeaders = mapOf("Authorization" to listOf("Bearer et-gyldig-token"), "Nav-Call-Id" to listOf("abc")),
        responseHeaders = mapOf("Content-Type" to listOf("application/json")),
        statusCode = 500,
        attempts = 3,
    )

    @Test
    fun `metadata lekker hverken innhold eller headerverdier i toString`() {
        val tekst = metadata().toString()

        tekst shouldNotContain fnr
        tekst shouldNotContain "Ola Nordmann"
        tekst shouldNotContain "et-gyldig-token"
        // Navnene på headerne er trygge, og er det eneste som trengs for å se hva som faktisk ble sendt.
        tekst shouldContain "Authorization"
        tekst shouldContain "Nav-Call-Id"
        tekst shouldContain "statusCode=500"
        tekst shouldContain "attempts=3"
        tekst shouldContain "<${personIRespons.length} tegn, maskert>"
    }

    @Test
    fun `en respons som aldri kom vises som null, ikke som maskert innhold`() {
        tomMetadata(rawRequestString = fnrIRequest, rawResponseString = null).toString() shouldContain "rawResponseString=null"
    }

    @Test
    fun `UventetStatus maskerer bodyen`() {
        val tekst = HttpKlientError.UventetStatus(statusCode = 500, body = personIRespons, metadata = metadata()).toString()

        tekst shouldNotContain "Ola Nordmann"
        tekst shouldNotContain fnr
        tekst shouldContain "statusCode=500"
        tekst shouldContain "retryable=true"
    }

    @Test
    fun `DeserializationError maskerer bodyen`() {
        val tekst = HttpKlientError.DeserializationError(
            throwable = IllegalStateException("parsefeil"),
            body = personIRespons,
            statusCode = 200,
            metadata = metadata(),
        ).toString()

        tekst shouldNotContain "Ola Nordmann"
        tekst shouldContain "parsefeil"
        tekst shouldContain "statusCode=200"
    }

    /** Fnr er allerede en Personopplysning og maskerer seg selv; testen pinner at den garantien gjelder gjennom en omsluttende data class. */
    @Test
    fun `en data class som inneholder maskerte felter arver maskeringen`() {
        data class Feilkontekst(val metadata: HttpKlientMetadata)

        Feilkontekst(metadata()).toString() shouldNotContain "et-gyldig-token"
        HttpKlientError.UventetStatus(500, personIRespons, metadata()).retryable shouldBe true
    }
}
