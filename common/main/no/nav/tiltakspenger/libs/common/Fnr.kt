package no.nav.tiltakspenger.libs.common

data class Fnr private constructor(
    val verdi: String,
) {

    private val fnrPattern = Regex("[0-9]{11}")

    init {
        validate(verdi)
    }

    override fun toString(): String = "***********"

    private fun validate(fnr: String) {
        // TODO post-mvp jah: På et tidspunkt vil vi måtte akseptere fødselsnumre som ikke er 11 siffer. Kanskje det er like greit å bare sjekke at den ikke er non-blank og trimme spaces på begge sider?
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
