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
        require(stringValue.contains("_")) { "Ikke gyldig Id ($stringValue), skal bestå av to deler skilt med _" }
        require(stringValue.split("_").size == 2) { "Ikke gyldig Id ($stringValue), skal bestå av prefiks + ulid" }
        require(stringValue.split("_").first().isNotEmpty()) { "Ikke gyldig Id ($stringValue), prefiks er tom" }
        try {
            ULID.parseULID(stringValue.split("_").last())
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
