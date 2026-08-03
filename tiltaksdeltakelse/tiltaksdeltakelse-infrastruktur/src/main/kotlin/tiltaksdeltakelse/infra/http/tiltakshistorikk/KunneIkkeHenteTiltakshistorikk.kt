package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk

/**
 * Hentingen av tiltakshistorikk feilet.
 *
 * Feilene er allerede logget der de oppsto — hente-tjenesten logger hver feilsituasjon nøyaktig én gang — så variantene bærer bare det konsumentene trenger for å skille og telle.
 */
sealed interface KunneIkkeHenteTiltakshistorikk {
    /** Identoppslaget mot PDL feilet på kall-nivå — uten identer kan ikke historikken slås opp. */
    data object IdentoppslagFeilet : KunneIkkeHenteTiltakshistorikk

    /** Kallet mot tiltakshistorikk feilet — nettverk, timeout eller uventet status. */
    data object KallFeilet : KunneIkkeHenteTiltakshistorikk

    /**
     * Svaret lot seg ikke tolke, og hele oppslaget feiler høylytt i stedet for at rader forsvinner stille.
     * Typisk en blank kode, en deltakelse uten type-diskriminator, en rad for en ident det ikke ble spurt om, eller dupliserte deltakelses-ider.
     */
    data class UgyldigRespons(
        val beskrivelse: String,
    ) : KunneIkkeHenteTiltakshistorikk
}
