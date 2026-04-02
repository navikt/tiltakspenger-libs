package no.nav.tiltakspenger.libs.common

import ulid.ULID
import java.util.UUID

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
