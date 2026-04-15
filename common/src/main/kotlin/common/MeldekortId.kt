package no.nav.tiltakspenger.libs.common

import ulid.ULID
import java.util.UUID

/**
 * Brukes for meldekortbehandlinger. For rammebehandling, se [BehandlingId].
 * Brukes på tvers av tiltakspenger sine egne tjenester.
 */
data class MeldekortId private constructor(private val ulid: UlidBase) : Ulid by ulid {
    companion object {
        const val PREFIX = "meldekort"
        fun random() = MeldekortId(ulid = UlidBase("${PREFIX}_${ULID.randomULID()}"))

        fun fromString(stringValue: String): MeldekortId {
            require(stringValue.startsWith(PREFIX)) { "Prefix må starte med $PREFIX. Dette er nok ikke en MeldekortId ($stringValue)" }
            return MeldekortId(ulid = UlidBase(stringValue))
        }

        fun fromUUID(uuid: UUID) = MeldekortId(ulid = UlidBase("${PREFIX}_${uuidToUlid(uuid)}"))
    }
}
