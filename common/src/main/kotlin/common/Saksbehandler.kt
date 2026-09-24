package no.nav.tiltakspenger.libs.common

/**
 * @param scopes Tilsvarer groups for systembruker.
 * Dette er tilgangen vi gir til brukeren i regi av systembruker (applikasjon som kaller oss).
 * TODO abn: lag en separat DTO klasse
 */
data class Saksbehandler(
    override val navIdent: String,
    // TODO post-mvp: Dersom brukernavn og epost ikke brukes, fjerne disse fra Saksbehandler.
    // Brukernavn utledes fra epost og det føles ikke idéelt.
    val brukernavn: String,
    val epost: String,
    override val roller: Saksbehandlerroller,
    val scopes: GenerellSystembrukerroller<GenerellSystembrukerrolle>,
    override val klientId: String,
    override val klientnavn: String,
) : Bruker<Saksbehandlerrolle, Saksbehandlerroller> {

    val erSaksbehandler: Boolean by lazy { roller.erSaksbehandler }

    val erBeslutter: Boolean by lazy { roller.erBeslutter }

    val erSaksbehandlerEllerBeslutter: Boolean by lazy { roller.erSaksbehandlerEllerBeslutter }

    override fun toString(): String =
        "Saksbehandler(navIdent='$navIdent', brukernavn='*****', epost='*****', roller=$roller)"
}
