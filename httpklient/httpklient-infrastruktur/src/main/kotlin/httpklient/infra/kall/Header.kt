package no.nav.tiltakspenger.libs.httpklient.infra.kall

/**
 * En request-header konsumenten setter på et kall.
 *
 * `Content-Type`, `Accept`, `Authorization`, `Content-Length` og `Host` er reserverte og avvises her (fail-fast i init): de eies av klienten og er en konsekvens av hvilken metode du kaller og hvilken auth som er konfigurert — ikke noe call sites setter selv.
 * Trenger du en annen `Accept`/`Content-Type`, mangler det en metode på `HttpKlient`; legg den til i libs i stedet for å omgå regelen.
 *
 * [sensitiv] = `true` maskerer verdien i [no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata.rawRequestString] (f.eks. meldekortservicens `ident`-header som bærer fnr).
 * Selve HTTP-requesten sendes alltid med ekte verdi; maskeringen gjelder kun tekstrepresentasjonen som havner i logger.
 */
data class Header(
    val navn: String,
    val verdi: String,
    val sensitiv: Boolean = false,
) {
    init {
        require(navn.isNotBlank()) { "Headernavn kan ikke være blankt" }
        require(navn.lowercase() !in reserverteHeaderNavn) {
            "Headeren '$navn' eies av HttpKlient og settes automatisk av metoden du kaller."
        }
    }

    companion object {
        private val reserverteHeaderNavn = setOf("content-type", "accept", "authorization", "content-length", "host")
    }
}

/**
 * Én sannhetskilde for headernavnene tiltakspenger-appene faktisk bruker mot eksterne tjenester i dag.
 * Poenget er å fjerne fritekst-stavemåter på call sites — inkludert de bevisst ulike variantene ([navCallId] vs [navCallid]) som ulike nedstrømstjenester krever.
 */
object NavHeadere {
    fun xCorrelationId(verdi: String) = Header("X-Correlation-ID", verdi)

    fun navCallId(verdi: String) = Header("Nav-Call-Id", verdi)

    /** Dokarkiv/dokdist-varianten med liten `i` i `id`. */
    fun navCallid(verdi: String) = Header("Nav-Callid", verdi)

    fun navConsumerId(verdi: String) = Header("Nav-Consumer-Id", verdi)

    /** PDL. */
    fun tema(verdi: String) = Header("Tema", verdi)

    /** PDL. */
    fun behandlingsnummer(verdi: String) = Header("behandlingsnummer", verdi)

    /**
     * Meldekortservice.
     * Fnr i klartekst, derfor sensitiv (maskeres i rawRequestString).
     */
    fun ident(fnr: String) = Header("ident", fnr, sensitiv = true)
}
