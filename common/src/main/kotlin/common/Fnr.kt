package no.nav.tiltakspenger.libs.common

data class Fnr private constructor(
    val verdi: String,
) {
    init {
        validate(verdi)
    }

    override fun toString(): String = "***********"

    companion object {
        private val FNR_PATTERN = Regex("[0-9]{11}")

        private fun validate(fnr: String) {
            if (!fnr.matches(FNR_PATTERN)) throw UgyldigFnrException(fnr)
        }

        /**
         * @return null hvis fnr er ugyldig. Regel: [FNR_PATTERN]
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
         * @throws UgyldigFnrException hvis fnr er ugyldig. Regel: [FNR_PATTERN]
         */
        fun fromString(fnr: String): Fnr {
            return Fnr(fnr)
        }
    }
}

data class UgyldigFnrException(@Suppress("unused") val unparsed: String) : RuntimeException("Ugyldig fnr.") {
    override fun toString(): String = "***********"
}
