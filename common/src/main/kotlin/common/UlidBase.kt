package no.nav.tiltakspenger.libs.common

import ulid.ULID
import java.nio.ByteBuffer
import java.util.UUID

data class UlidBase(
    private val stringValue: String,
) : Ulid {
    companion object {
        fun random(prefix: String): UlidBase {
            require(prefix.isNotEmpty()) { "Prefiks er tom" }
            return UlidBase("${prefix}_${ULID.randomULID()}")
        }

        fun fromDb(stringValue: String) = UlidBase(stringValue)
    }

    init {
        val parts = stringValue.split("_")
        require(parts.size == 2) { "Ikke gyldig Id ($stringValue), skal bestå av prefiks + ulid" }
        require(parts[0].isNotEmpty()) { "Ikke gyldig Id ($stringValue), prefiks er tom" }
        try {
            ULID.parseULID(parts[1])
        } catch (e: Exception) {
            throw IllegalArgumentException("Ikke gyldig Id ($stringValue), ulid er ugyldig")
        }
    }

    override fun prefixPart(): String = stringValue.split("_").first()

    override fun ulidPart(): String = stringValue.split("_").last()

    /** Brukes for å generere ID som sendes til hel ved og OS/UR. De har en begrensning på 30 tegn. */
    override fun uuidPart() = this.ulidPart().substring(11, 26)

    override fun toString() = stringValue

    override fun compareTo(other: Ulid) = this.toString().compareTo(other.toString())
}

fun uuidToUlid(uuid: UUID): ULID {
    val buffer = ByteBuffer.allocate(16)
    buffer.putLong(uuid.mostSignificantBits)
    buffer.putLong(uuid.leastSignificantBits)
    return ULID.Factory().fromBytes(buffer.array())
}
