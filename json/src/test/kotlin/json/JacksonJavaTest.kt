package no.nav.tiltakspenger.libs.json

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID

/**
 * SerDes-kontrakt for typer fra Java stdlib (utenom `java.time` — det er dekket av [JacksonJavaTimeTest]).
 * Eksakt JSON-format + round-trip.
 */
internal class JacksonJavaTest {

    @Test
    fun `UUID round-trip`() {
        roundTrip(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            """"00000000-0000-0000-0000-000000000001"""",
        )
    }

    @Test
    fun `UUID som felt i DTO`() {
        val id = UUID.fromString("11111111-2222-3333-4444-555555555555")
        roundTrip(MedUuid(id = id, navn = "x"), """{"id":"$id","navn":"x"}""")
    }

    @Test
    fun `BigDecimal bevarer skala og presisjon`() {
        roundTrip(BigDecimal("12345.6789"), "12345.6789")
        // Skala på trailing-nuller skal bevares — viktig for økonomi/satser.
        roundTrip(BigDecimal("10.00"), "10.00")
    }

    @Test
    fun `BigInteger med tall større enn Long MAX_VALUE`() {
        roundTrip(BigInteger("12345678901234567890"), "12345678901234567890")
    }

    @Test
    fun `BigDecimal som felt i DTO`() {
        roundTrip(
            MedBigDecimal(navn = "sats", beløp = BigDecimal("100.50")),
            """{"navn":"sats","beløp":100.50}""",
        )
    }
}

private data class MedUuid(val id: UUID, val navn: String)

private data class MedBigDecimal(val navn: String, val beløp: BigDecimal)
