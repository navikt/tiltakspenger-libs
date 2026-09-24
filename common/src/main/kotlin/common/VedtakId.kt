package no.nav.tiltakspenger.libs.common

import ulid.ULID
import java.util.UUID

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
