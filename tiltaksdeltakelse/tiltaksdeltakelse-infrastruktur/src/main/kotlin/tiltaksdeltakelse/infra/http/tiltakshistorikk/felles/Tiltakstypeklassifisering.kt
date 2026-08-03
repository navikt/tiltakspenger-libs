package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett

// Klassifiseringen av en tiltakskode til domenets Tiltakstype, delt mellom kildene — tabellene bor i Arenakoder, Kometkoder og TeamTiltakkoder.
// Tabellene er data, ikke kontrollflyt: en kode slås opp, og utfallet er SomGirRett, SomIkkeGirRett eller Ukjent.
// Skillet mellom de to siste krever at vi kjenner kildens vokabular: en kode vi aldri har sett kan i prinsippet gi rett og skal varsles på, mens en kjent kode uten rett er hverdagskost.
// Ukjente koder blir aldri valueOf — det er en påregnelig hendelse, ikke en feil.

internal fun tiltakstype(
    tiltakskode: String,
    somGirRett: Map<String, TiltakstypeSomGirRett>,
    utenRett: Set<String>,
    kilde: String,
): Either<UgyldigKontraktsverdi, Tiltakstype> {
    if (tiltakskode.isBlank()) {
        return UgyldigKontraktsverdi("Blank tiltakskode fra $kilde kan ikke bæres som ukjent kildeverdi").left()
    }
    val girRett = somGirRett[tiltakskode]
    return when {
        girRett != null -> Tiltakstype.SomGirRett(tiltakskodeFraKilden = tiltakskode, tiltakstype = girRett)
        tiltakskode in utenRett -> Tiltakstype.SomIkkeGirRett(tiltakskodeFraKilden = tiltakskode)
        else -> Tiltakstype.Ukjent(tiltakskodeFraKilden = tiltakskode)
    }.right()
}
