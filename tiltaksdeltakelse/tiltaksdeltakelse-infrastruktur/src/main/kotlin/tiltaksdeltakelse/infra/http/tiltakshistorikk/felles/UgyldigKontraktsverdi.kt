package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles

/**
 * En verdi fra kontrakten som ikke kan representeres i domenet — i praksis en blank kode der kontrakten lover et enum-navn.
 * Domenets ukjent-varianter krever en ikke-blank kode, så en blank verdi er søppel og skal felle hele svaret høylytt i stedet for å forsvinne stille.
 * Det er samme klasse kontraktsbrudd som når et påkrevd felt mangler i JSON-en, og behandles likt: modellert feil, aldri kast, aldri stille rad-dropp.
 */
data class UgyldigKontraktsverdi(
    /**
     * Hva som var galt, med vår ordlyd.
     *
     * **Trygg i vanlig logg**, og skal forbli det: teksten settes sammen av faste ledd, og det eneste som interpoleres er vår egen [no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakskilde].
     * Den gale verdien settes bevisst ikke inn — den er per definisjon blank når den utløser dette, og verdier fra kilden hører uansett i responsens metadata.
     * Feltet bæres videre som `KunneIkkeHenteTiltakshistorikk.UgyldigRespons.beskrivelse`, så garantien gjelder helt ut til konsumenten.
     */
    val beskrivelse: String,
)
