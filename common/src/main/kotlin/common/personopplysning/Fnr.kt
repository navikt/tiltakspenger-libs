package no.nav.tiltakspenger.libs.common.personopplysning

/**
 * Fødselsnummer er en ellevesifret identifikator for personer, se https://www.skatteetaten.no/person/folkeregister/fodsel-og-navnevalg/barn-fodt-i-norge/fodselsnummer/.
 * Kan identifisere personen og røpe fødselsdato og kjønn gjennom nummerets oppbygning.
 * Begrepet forvaltes av Folkeregisteret hos Skatteetaten.
 * Krever nøyaktig elleve sifre; kontrollsiffer valideres ikke her.
 */
data class Fnr private constructor(
    val verdi: String,
) : Personopplysning {
    init {
        validate(verdi)
    }

    override val begrunnelse: String
        get() = "Fødselsnummer identifiserer en person entydig og koder fødselsdato i de seks første sifrene og kjønn i personnummeret. På avveie kan nummeret derfor utlevere mer enn identiteten alene, også uten oppslag mot Folkeregisteret."

    override fun toString(): String = "***********"

    companion object {
        private val FNR_PATTERN = Regex("[0-9]{11}")

        private fun validate(fnr: String) {
            if (!fnr.matches(FNR_PATTERN)) throw UgyldigFnrException(fnr)
        }

        /**
         * @return null hvis fnr er ugyldig.
         * Regel: [FNR_PATTERN]
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
         * @throws UgyldigFnrException hvis fnr er ugyldig.
         * Regel: [FNR_PATTERN]
         */
        fun fromString(fnr: String): Fnr {
            return Fnr(fnr)
        }
    }
}

data class UgyldigFnrException(@Suppress("unused") val unparsed: String) : RuntimeException("Ugyldig fnr.") {
    override fun toString(): String = "***********"
}
