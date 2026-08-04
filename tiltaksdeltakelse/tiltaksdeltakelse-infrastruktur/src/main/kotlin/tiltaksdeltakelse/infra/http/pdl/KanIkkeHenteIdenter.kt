package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl

import arrow.core.Nel
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata

/**
 * Identoppslaget mot PDL ga ikke identer å bruke.
 *
 * Skillet mellom variantene er det hente-tjenesten trenger for fallback-reglene: et kall som feilet feller hele oppslaget, mens et svar uten brukbare identer faller tilbake til innsendt fnr.
 * Derfor er de tre siste samlet under [UtenBrukbareIdenter] — da kan et fallback-utfall ikke bære [KallFeilet], og skillet er en type i stedet for en regel noen må huske.
 *
 * Klienten logger aldri selv, og hente-tjenesten over den gjør det heller ikke — samme regel som i `httpklient`.
 * Hvert felt sier derfor selv om det tåler vanlig logg.
 */
sealed interface KanIkkeHenteIdenter {
    /** Selve kallet feilet — nettverk, timeout eller uventet status. */
    data class KallFeilet(
        /** Bærer selv skillet mellom vanlig logg og sikkerlogg — bruk `loggFeil`, ikke `toString()`. */
        val httpKlientError: HttpKlientError,
    ) : KanIkkeHenteIdenter

    /**
     * PDL svarte, men ga oss ingen identer vi kan bruke.
     * Oppslaget kan fortsette på innsendt fnr; det er ikke gitt at det er riktig, men det er brukbart.
     */
    sealed interface UtenBrukbareIdenter : KanIkkeHenteIdenter {
        /**
         * Rå request og respons fra PDL-kallet.
         *
         * **Kun sikkerlogg.**
         * Requesten inneholder fødselsnummeret det ble spurt for, og responsen personens identhistorikk.
         */
        val metadata: HttpKlientMetadata

        /** PDL svarte 200, men med funksjonelle feil i errors-lista. */
        data class GraphQLFeil(
            /**
             * Feilmeldingene fra PDL, ordrett.
             *
             * **Kun sikkerlogg.**
             * Dette er kildens tekst, ikke vår, og PDL gir ingen garanti for at den er fri for personopplysninger — dagens kjede i `tiltakspenger-tiltak` la den derfor kun i sikkerlogg, og det skal vi fortsette med.
             *
             * Alle feilene bæres — kontrakten tillater flere i samme svar, og den første sier sjelden hele historien.
             * [Nel] framfor `List` fordi varianten bare finnes når det faktisk er minst én feil.
             */
            val feilmeldinger: Nel<String>,
            override val metadata: HttpKlientMetadata,
        ) : UtenBrukbareIdenter

        /** PDL svarte uten feil, men identlisten var tom eller manglet. */
        data class FantIngenIdenter(
            override val metadata: HttpKlientMetadata,
        ) : UtenBrukbareIdenter

        /** PDL svarte med en ident som ikke er et gyldig fødselsnummer — svaret stoles ikke på. */
        data class UgyldigIdent(
            override val metadata: HttpKlientMetadata,
        ) : UtenBrukbareIdenter
    }
}
