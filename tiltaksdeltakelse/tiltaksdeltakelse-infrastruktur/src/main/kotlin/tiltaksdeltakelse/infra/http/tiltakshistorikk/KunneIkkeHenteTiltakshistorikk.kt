package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk

import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata

/**
 * Hentingen av tiltakshistorikk feilet.
 *
 * Ingenting her er logget når feilen når konsumenten — hverken klientene eller hente-tjenesten logger selv, samme regel som i `httpklient`.
 * Variantene bærer derfor alt konsumenten trenger for å logge én gang, på det nivået konsumenten mener er riktig: en skygge som feiler er ikke en driftsfeil, mens den samme feilen på den ekte veien er det.
 *
 * **Hvert felt sier selv om det tåler vanlig logg.**
 * Hovedregelen er enkel: tekst vi har skrevet selv er trygg, alt som kommer fra kilden hører i sikkerlogg.
 * `HttpKlientError.loggFeil` gjør den delingen ferdig for de to kall-variantene.
 */
sealed interface KunneIkkeHenteTiltakshistorikk {
    /**
     * Identoppslaget mot PDL feilet på kall-nivå — uten identer kan ikke historikken slås opp.
     * Et PDL-svar som bare manglet brukbare identer er ikke en feil her; da faller oppslaget tilbake til innsendt fnr, og det står på [Identoppslag].
     */
    data class IdentoppslagFeilet(
        /** Bærer selv skillet mellom vanlig logg og sikkerlogg — bruk `loggFeil`, ikke `toString()`. */
        val httpKlientError: HttpKlientError,
    ) : KunneIkkeHenteTiltakshistorikk

    /** Kallet mot tiltakshistorikk feilet — nettverk, timeout eller uventet status. */
    data class KallFeilet(
        /** Bærer selv skillet mellom vanlig logg og sikkerlogg — bruk `loggFeil`, ikke `toString()`. */
        val httpKlientError: HttpKlientError,
    ) : KunneIkkeHenteTiltakshistorikk

    /**
     * Svaret lot seg ikke tolke, og hele oppslaget feiler høylytt i stedet for at rader forsvinner stille.
     * Typisk en blank kode, en deltakelse uten type-diskriminator, en rad for en ident det ikke ble spurt om, eller dupliserte deltakelses-ider.
     */
    data class UgyldigRespons(
        /**
         * Hva som var galt, med vår ordlyd — for eksempel «Svaret inneholder dupliserte deltakelses-ider» eller «Blank deltakerstatus fra Arena kan ikke bæres som ukjent kildeverdi».
         *
         * **Trygg i vanlig logg.**
         * Teksten er skrevet av oss og satt sammen av faste ledd; det eneste som settes inn er vår egen [no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakskilde].
         * Ingen verdi fra svaret havner her — heller ikke den gale verdien, som per definisjon er blank når den utløser dette.
         * Skal noe fra kilden med i en ny beskrivelse, hører det i [metadata] i stedet, ellers slutter dette feltet å være trygt uten at noen merker det.
         */
        val beskrivelse: String,
        /**
         * Rå request og respons fra kallet.
         *
         * **Kun sikkerlogg.**
         * Responsen er personopplysninger — den inneholder hele tiltakshistorikken til en navngitt person.
         * Til gjengjeld er den det eneste som forteller *hvilken* rad som var gal, så den er verdt å ha med når noe skal feilsøkes.
         */
        val metadata: HttpKlientMetadata,
    ) : KunneIkkeHenteTiltakshistorikk
}
