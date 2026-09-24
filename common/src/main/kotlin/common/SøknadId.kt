package no.nav.tiltakspenger.libs.common

import ulid.ULID
import java.util.UUID

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
