package no.nav.tiltakspenger.libs.common.personopplysning

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class OrganisasjonsnummerTest {
    @Test
    fun `organisasjonsnummer maskerer seg selv og i data-klasser`() {
        val organisasjonsnummer = Organisasjonsnummer("123456789")

        organisasjonsnummer.toString() shouldBe "*****"
        organisasjonsnummer.toString() shouldNotContain "123456789"

        data class Utbetaling(val organisasjonsnummer: Organisasjonsnummer)

        Utbetaling(organisasjonsnummer).toString() shouldBe "Utbetaling(organisasjonsnummer=*****)"
        Utbetaling(organisasjonsnummer).toString() shouldNotContain "123456789"
    }

    @Test
    fun `begrunnelse er ikke tom`() {
        Organisasjonsnummer("000000000").begrunnelse.isNotBlank() shouldBe true
    }

    @Test
    fun `blankt organisasjonsnummer avvises`() {
        shouldThrowWithMessage<IllegalArgumentException>("Organisasjonsnummer kan ikke være tomt") {
            Organisasjonsnummer("")
        }
        shouldThrowWithMessage<IllegalArgumentException>("Organisasjonsnummer kan ikke være tomt") {
            Organisasjonsnummer("   ")
        }
    }

    @Test
    fun `fabrikken gjør blank til fravær`() {
        organisasjonsnummer(null) shouldBe null
        organisasjonsnummer("") shouldBe null
        organisasjonsnummer("   ") shouldBe null
        organisasjonsnummer("000000000") shouldBe Organisasjonsnummer("000000000")
    }
}
