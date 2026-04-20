package no.nav.tiltakspenger.libs.common

import ulid.ULID
import java.util.UUID

data class RammebehandlingId private constructor(
    private val ulid: UlidBase,
) : Ulid by ulid {
    companion object {
        const val PREFIX = "beh"

        fun random() = RammebehandlingId(ulid = UlidBase("${PREFIX}_${ULID.randomULID()}"))

        fun fromString(stringValue: String): RammebehandlingId {
            require(stringValue.startsWith(PREFIX)) { "Prefix må starte med $PREFIX. Dette er nok ikke en BehandlingId ($stringValue)" }
            return RammebehandlingId(ulid = UlidBase(stringValue))
        }

        fun fromUUID(uuid: UUID) = RammebehandlingId(ulid = UlidBase("${PREFIX}_${uuidToUlid(uuid)}"))
    }
}
