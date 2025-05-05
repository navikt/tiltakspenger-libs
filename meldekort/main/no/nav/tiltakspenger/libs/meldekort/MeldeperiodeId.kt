package no.nav.tiltakspenger.libs.meldekort

import ulid.ULID
import java.util.UUID

data class MeldeperiodeId private constructor(
    private val ulid: UlidBase,
) : Ulid by ulid {
    companion object {
        private const val PREFIX = "meldeperiode"

        fun random() = MeldeperiodeId(ulid = UlidBase("${PREFIX}_${ULID.randomULID()}"))

        fun fromString(stringValue: String): MeldeperiodeId {
            require(stringValue.startsWith(PREFIX)) { "Prefix må starte med $PREFIX. Dette er nok ikke en MeldeperiodeId ($stringValue)" }
            return MeldeperiodeId(ulid = UlidBase(stringValue))
        }

        fun fromUUID(uuid: UUID) = MeldeperiodeId(ulid = UlidBase("${PREFIX}_${uuidToUlid(uuid)}"))
    }
}
