package no.nav.tiltakspenger.libs.common

import java.util.UUID

interface Ulid : Comparable<Ulid> {
    fun prefixPart(): String

    fun ulidPart(): String

    fun uuid(): UUID = ulidToUuid(ulidPart())

    fun uuidPart(): String

    override fun toString(): String
}

private fun ulidToUuid(ulid: String): UUID {
    val (most, least) = ulidStringToLongs(ulid)
    return UUID(most, least)
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
        mostSig = mostSig or carry
    }
    return Pair(mostSig, leastSig)
}
