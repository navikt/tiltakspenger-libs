package no.nav.tiltakspenger.libs.common

import ulid.ULID
import java.util.UUID

/**
 * Brukes for rammebehandlinger. For meldekortbehandling, se [MeldekortId].
 * Brukes på tvers av tiltakspenger sine egne tjenester.
 */
data class BehandlingId private constructor(
    private val ulid: UlidBase,
) : Ulid by ulid {
    companion object {
        const val PREFIX = "beh"

        fun random() = BehandlingId(ulid = UlidBase("${PREFIX}_${ULID.randomULID()}"))

        fun fromString(stringValue: String): BehandlingId {
            require(stringValue.startsWith(PREFIX)) { "Prefix må starte med $PREFIX. Dette er nok ikke en BehandlingId ($stringValue)" }
            return BehandlingId(ulid = UlidBase(stringValue))
        }

        fun fromUUID(uuid: UUID) = BehandlingId(ulid = UlidBase("${PREFIX}_${uuidToUlid(uuid)}"))
    }
}
