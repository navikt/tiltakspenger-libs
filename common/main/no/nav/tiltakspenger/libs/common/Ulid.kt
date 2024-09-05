package no.nav.tiltakspenger.libs.common

import ulid.ULID
import java.nio.ByteBuffer
import java.util.UUID

interface Ulid : Comparable<Ulid> {
    fun prefixPart(): String

    fun ulidPart(): String

    fun uuid(): UUID = ulidToUuid(ulidPart())

    fun uuidPart(): String

    override fun toString(): String
}

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

// Ikke legg inn IDer som ikke brukes på tvers av repoer i fellesbiblioteket.

/** Brukes på tvers av tiltakspenger sine egne tjenester. */
data class SakId private constructor(
    private val ulid: UlidBase,
) : Ulid by ulid {
    companion object {
        private const val PREFIX = "sak"

        fun random() = SakId(ulid = UlidBase("${PREFIX}_${ULID.randomULID()}"))

        fun fromString(stringValue: String): SakId {
            require(stringValue.startsWith(PREFIX)) { "Prefix må starte med $PREFIX. Dette er nok ikke en SakId ($stringValue)" }
            return SakId(ulid = UlidBase(stringValue))
        }

        fun fromUUID(uuid: UUID) = SakId(ulid = UlidBase("${PREFIX}_${uuidToUlid(uuid)}"))
    }
}

/** Brukes på tvers av tiltakspenger sine egne tjenester. */
data class BehandlingId private constructor(
    private val ulid: UlidBase,
) : Ulid by ulid {
    companion object {
        private const val PREFIX = "beh"

        fun random() = BehandlingId(ulid = UlidBase("${PREFIX}_${ULID.randomULID()}"))

        fun fromString(stringValue: String): BehandlingId {
            require(stringValue.startsWith(PREFIX)) { "Prefix må starte med $PREFIX. Dette er nok ikke en BehandlingId ($stringValue)" }
            return BehandlingId(ulid = UlidBase(stringValue))
        }

        fun fromUUID(uuid: UUID) = BehandlingId(ulid = UlidBase("${PREFIX}_${uuidToUlid(uuid)}"))
    }
}

/** Brukes på tvers av tiltakspenger sine egne tjenester. */
data class SøknadId private constructor(
    private val ulid: UlidBase,
) : Ulid by ulid {
    companion object {
        private const val PREFIX = "soknad"

        fun random() = SøknadId(ulid = UlidBase("${PREFIX}_${ULID.randomULID()}"))

        fun fromString(stringValue: String): SøknadId {
            require(stringValue.startsWith(PREFIX)) { "Prefix må starte med $PREFIX. Dette er nok ikke en SøknadId ($stringValue)" }
            return SøknadId(ulid = UlidBase(stringValue))
        }

        fun fromUUID(uuid: UUID) = SøknadId(ulid = UlidBase("${PREFIX}_${uuidToUlid(uuid)}"))
    }
}

/** Brukes på tvers av tiltakspenger sine egne tjenester. */
data class VedtakId private constructor(
    private val ulid: UlidBase,
) : Ulid by ulid {
    companion object {
        private const val PREFIX = "vedtak"

        fun random() = VedtakId(ulid = UlidBase("${PREFIX}_${ULID.randomULID()}"))

        fun fromString(stringValue: String): VedtakId {
            require(stringValue.startsWith(PREFIX)) { "Prefix må starte med $PREFIX. Dette er nok ikke en VedtakId ($stringValue)" }
            return VedtakId(ulid = UlidBase(stringValue))
        }

        fun fromUUID(uuid: UUID) = VedtakId(ulid = UlidBase("${PREFIX}_${uuidToUlid(uuid)}"))
    }
}

/** Brukes på tvers av tiltakspenger sine egne tjenester. */
data class MeldekortId private constructor(private val ulid: UlidBase) : Ulid by ulid {
    companion object {
        private const val PREFIX = "meldekort"
        fun random() = MeldekortId(ulid = UlidBase("${PREFIX}_${ULID.randomULID()}"))

        fun fromString(stringValue: String): MeldekortId {
            require(stringValue.startsWith(PREFIX)) { "Prefix må starte med $PREFIX. Dette er nok ikke en MeldekortId ($stringValue)" }
            return MeldekortId(ulid = UlidBase(stringValue))
        }

        fun fromString(uuid: UUID) = MeldekortId(ulid = UlidBase("${PREFIX}_${uuidToUlid(uuid)}"))
    }
}

private fun ulidToUuid(ulid: String): UUID {
    val (most, least) = ulidStringToLongs(ulid)
    return UUID(most, least)
}

fun uuidToUlid(uuid: UUID): ULID {
    val buffer = ByteBuffer.allocate(16)
    buffer.putLong(uuid.mostSignificantBits)
    buffer.putLong(uuid.leastSignificantBits)
    return ULID.Factory().fromBytes(buffer.array())
}

private fun ulidStringToLongs(s: String): Pair<Long, Long> {
    val charMapBase32 =
        byteArrayOf(
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            0,
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            9,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            10,
            11,
            12,
            13,
            14,
            15,
            16,
            17,
            1,
            18,
            19,
            1,
            20,
            21,
            0,
            22,
            23,
            24,
            25,
            26,
            -1,
            27,
            28,
            29,
            30,
            31,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            10,
            11,
            12,
            13,
            14,
            15,
            16,
            17,
            1,
            18,
            19,
            1,
            20,
            21,
            0,
            22,
            23,
            24,
            25,
            26,
            -1,
            27,
            28,
            29,
            30,
            31,
        )

    fun base32CharToByte(ch: Char): Byte = charMapBase32[ch.code]

    var mostSig = 0L
    var leastSig = 0L
    for (i in 0..25) {
        val v = base32CharToByte(s[i])
        val carry = leastSig ushr 59 // 64 - 5
        leastSig = leastSig shl 5
        leastSig = leastSig or v.toLong()
        mostSig = mostSig shl 5
        mostSig = mostSig or carry.toLong()
    }
    return Pair(mostSig, leastSig)
}
