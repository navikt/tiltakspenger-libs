package no.nav.tiltakspenger.libs.httpklient

/**
 * Om requestens URI kan stå i vanlig logg, eller bare i sikkerlogg.
 *
 * `httpklient` kan ikke avgjøre dette selv.
 * URIene i APIene våre er stort sett personopplysningsfrie, men et fødselsnummer i en path-variabel eller et query-parameter er like sensitivt der som i en request-body.
 * Klienten er det eneste laget som kjenner APIet sitt godt nok til å svare, og tar derfor stilling én gang i [no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig].
 *
 * Default er [KunSikkerlogg]: en klient som ikke har tatt stilling skal aldri kunne lekke en URI til vanlig logg.
 */
enum class UriSynlighet {
    /**
     * Hele URIen kan stå i vanlig logg.
     * Velges av klienter der hverken path eller query kan bære personopplysninger — typisk faste endepunkter der identen ligger i request-bodyen.
     */
    VanligLogg,

    /**
     * Vanlig logg får kun metode og host; hele URIen finnes bare i sikkerlogg, via [HttpKlientMetadata.rawRequestString].
     * Host er aldri en personopplysning, så den avkortede formen navngir fortsatt hvilken integrasjon kallet gikk mot.
     */
    KunSikkerlogg,
}
