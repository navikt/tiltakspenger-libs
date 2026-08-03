package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * En deltakelsesform fra kontrakten vi ikke kjenner igjen — `type`-diskriminatoren pekte på en variant vi ikke har.
 * Raden kan ikke bli en tiltaksdeltakelse, siden vi ikke kan anta noe om formen, men den skal varsles på — aldri forsvinne stille.
 * Bæres på hente-resultatet [Tiltakshistorikk], ikke på [Tiltaksdeltakelser] — den er en egenskap ved svaret, ikke ved en deltakelse.
 */
data class UkjentDeltakelsesform(
    override val kodeIKontrakten: String,
) : UkjentKildeverdi {
    init {
        require(kodeIKontrakten.isNotBlank()) { "En ukjent kildeverdi må bære kontraktens kode" }
    }

    override val hva: String get() = "deltakelsesform fra tiltakshistorikk"
}
