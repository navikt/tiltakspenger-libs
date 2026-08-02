package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Noe kilden sa som vi ikke kjenner igjen — med «hva» som førsteklasses begrep.
 *
 * Typene sier hver for seg hva som er ukjent, men visning og varsling skal slippe å kjenne hver akse.
 * Flaten samler dem: [hva] sier hvilken verdi det gjelder, [kodeIKontrakten] bærer det som faktisk sto på wiren.
 * Samme mønster som `Personopplysning.begrunnelse` i `common`: teksten er statisk per type, og skal kunne leses av andre enn utviklere.
 *
 * Implementeres av [Kildestatus.Ukjent]-variantene, [Kometårsak.Ukjent], [Tiltakshistorikkmelding.Ukjent] og [Tiltakstype.Ukjent].
 * Se [ukjenteKildeverdier] for alt som er ukjent ved én deltakelse.
 */
sealed interface UkjentKildeverdi {
    /**
     * Hva slags verdi det er, til visning og varsling — «deltakerstatus fra Arena», «årsak fra Komet», «tiltakskode fra kilden».
     * Statisk per type, ikke per instans.
     */
    val hva: String

    /**
     * Verdien slik kontrakten skrev den — det er nettopp denne vi ikke kjenner igjen.
     */
    val kodeIKontrakten: String
}

/**
 * Alt som er ukjent ved deltakelsen: status, tiltakskode og Komet-årsak.
 *
 * Tom liste betyr at alle kildeverdiene lot seg tolke.
 * Dette er stedet varsling og visning leser, i stedet for å kjenne hver akse for seg.
 */
val Tiltaksdeltakelse.ukjenteKildeverdier: List<UkjentKildeverdi>
    get() = buildList {
        val status = kildestatus
        if (status is Kildestatus.Ukjent) {
            add(status)
        }

        val årsak = (status as? Kometstatus)?.årsak
        if (årsak is Kometårsak.Ukjent) {
            add(årsak)
        }

        val ukjentTiltakskode = when (val deltakelse = this@ukjenteKildeverdier) {
            is Tiltaksdeltakelse.UkjentTiltakstype -> Tiltakstype.Ukjent(deltakelse.tiltakskodeFraKilden)

            is Tiltaksdeltakelse.Ugyldig -> deltakelse.tiltakstype as? Tiltakstype.Ukjent

            is Tiltaksdeltakelse.GirRett.MedPeriode,
            is Tiltaksdeltakelse.GirRett.UtenPeriode,
            is Tiltaksdeltakelse.GirIkkeRett,
            -> null
        }
        if (ukjentTiltakskode != null) {
            add(ukjentTiltakskode)
        }
    }
