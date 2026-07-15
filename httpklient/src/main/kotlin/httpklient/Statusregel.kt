package no.nav.tiltakspenger.libs.httpklient

/**
 * Hvilke HTTP-statuser som regnes som suksess for et kall.
 * Ren data (ingen predikater): verdiene kan sammenlignes, logges og asserters i tester — i motsetning til den gamle `(Int) -> Boolean`-modellen som brøt likhet og var uloggbar.
 * En status som ikke godtas gir [HttpKlientError.UventetStatus] med lesbar body og full metadata, som konsumenten kan utlede domenefeil fra (se [HttpKlientError.harStatus] og [HttpKlientError.ResponsMottatt.bodySomJson]).
 */
sealed interface Statusregel {
    /** Standard: alle `2xx`-statuser (200–299) regnes som suksess. */
    data object Alle2xx : Statusregel

    /**
     * Kun de eksplisitt oppgitte statuskodene regnes som suksess, f.eks. `Eksakt(202)` for utbetaling.
     * Statuser som betyr noe annet enn suksess (f.eks. tilgangsmaskinens `403` eller dokarkivs dedup-`409`) skal _ikke_ hit — les dem fra feiltypen i stedet, slik at suksesskanalen beholder én type.
     */
    data class Eksakt(val statuser: Set<Int>) : Statusregel {
        constructor(vararg statuser: Int) : this(statuser.toSet())

        init {
            require(this.statuser.isNotEmpty()) { "Eksakt må ha minst én statuskode" }
            this.statuser.forEach {
                require(it in 100..999) { "Statuskode må være tresifret, var $it" }
            }
        }
    }
}

/** Sant hvis [statusCode] regnes som suksess etter denne regelen. */
internal fun Statusregel.godtar(statusCode: Int): Boolean = when (this) {
    Statusregel.Alle2xx -> statusCode in 200..299
    is Statusregel.Eksakt -> statusCode in statuser
}
