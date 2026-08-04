package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk

import arrow.core.Nel
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikk
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl.KanIkkeHenteIdenter

/**
 * Et vellykket oppslag: historikken, og det konsumenten trenger for å logge det.
 *
 * [respons] er `httpklient` sin egen konvolutt, så `loggSuksess`, rå respons, status og antall forsøk er tilgjengelig uten at vi bygger et parallelt vokabular.
 * Suksess-logging er et krav for denne kjeden — dagens tjeneste i `tiltakspenger-tiltak` logger hver deltakelse til sikkerlogg — men *hente-tjenesten* gjør det ikke selv, den leverer materialet.
 */
data class TiltakshistorikkResultat(
    /**
     * Konvolutten fra kallet, med historikken som body.
     *
     * `statusCode` og `attempts` er trygge i vanlig logg; `rawRequestString` og `rawResponseString` er personopplysninger og hører kun i sikkerlogg.
     * `loggSuksess` gjør den delingen ferdig.
     */
    val respons: HttpKlientResponse<Tiltakshistorikk>,
    val identoppslag: Identoppslag,
) {
    val tiltakshistorikk: Tiltakshistorikk get() = respons.body
}

/**
 * Hvilke identer historikken ble slått opp for, og hvor de kom fra.
 *
 * Fallbacken er verdt å vite om selv når oppslaget lykkes: vi kan ha fått historikken for færre identer enn personen faktisk har hatt, og da kan deltakelser mangle uten at noe feilet.
 * Den ble tidligere logget som en feil inne i hente-tjenesten; nå er den et utfall konsumenten kan telle og velge nivå på.
 *
 * [identer] er fødselsnumre og hører aldri i vanlig logg — bruk antallet der, og identene kun i sikkerlogg.
 */
sealed interface Identoppslag {
    val identer: Nel<Fnr>

    /** PDL svarte med identer, og de ble brukt (innsendt fnr er lagt til hvis PDL ikke returnerte det). */
    data class FraPdl(
        override val identer: Nel<Fnr>,
    ) : Identoppslag

    /**
     * PDL ga oss ingen brukbare identer, så oppslaget gikk på innsendt fnr alene.
     *
     * Kommentar John (portert fra tiltak-appen): I første omgang fallbacker vi bare til innsendt fnr for å få en myk overgang.
     * Lar denne feile ved null når vi har fjernet barnesykdommene.
     */
    data class FaltTilbakeTilInnsendtFnr(
        override val identer: Nel<Fnr>,
        val grunn: KanIkkeHenteIdenter.UtenBrukbareIdenter,
    ) : Identoppslag
}
