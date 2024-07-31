package no.nav.tiltakspenger.libs.common

/**
 * Kommentar jah: Denne kan vel flyttes til libs-common eller noe lignende?
 */
data class Fnr private constructor(
    val verdi: String,
) {

    private val fnrPattern = Regex("[0-9]{11}")

    init {
        validate(verdi)
    }

    override fun toString(): String = "***********"

    private fun validate(fnr: String) {
        if (!fnr.matches(fnrPattern)) throw UgyldigFnrException(fnr)
    }

    companion object {
        /**
         * @return null hvis fnr er ugyldig. Regel: [fnrPattern]
         */
        @Suppress("unused")
        fun tryFromString(fnr: String): Fnr? {
            return try {
                Fnr(fnr)
            } catch (e: UgyldigFnrException) {
                null
            }
        }

        /**
         * @throws UgyldigFnrException hvis fnr er ugyldig. Regel: [fnrPattern]
         */
        fun fromString(fnr: String): Fnr {
            return Fnr(fnr)
        }
    }
}

data class UgyldigFnrException(@Suppress("unused") val unparsed: String) : RuntimeException("Ugyldig fnr.") {
    override fun toString(): String = "***********"
}
