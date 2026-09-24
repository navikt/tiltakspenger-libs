package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import no.nav.tiltakspenger.libs.periode.Periode

/**
 * Alle tiltaksdeltakelsene vi mottok for en person.
 *
 * Wrapperen er stedet for invarianter og fellesfunksjoner — aldri en naken liste.
 * Ingenting er filtrert bort på vei inn: også [Tiltaksdeltakelse.Ugyldig], ukjente tiltakskoder og ukjente kildestatuser er med, og konsumentene snevrer inn med uttrekkene.
 *
 * Hentetidspunkt og ukjente deltakelsesformer hører ikke hjemme her: de er egenskaper ved *hentingen*, og bæres av [Tiltakshistorikk].
 */
data class Tiltaksdeltakelser(
    val deltakelser: List<Tiltaksdeltakelse>,
) {
    init {
        val duplikater = deltakelser.groupBy { it.id }.filterValues { gruppe -> gruppe.size > 1 }.keys
        require(duplikater.isEmpty()) { "Tiltaksdeltakelsene må ha unike id-er, fikk duplikater: $duplikater" }
    }

    /** Deltakelsene der tiltakstypen gir rett — det eneste utvalget beregning noen gang skal se. */
    val girRett: List<Tiltaksdeltakelse.GirRett> get() = deltakelser.filterIsInstance<Tiltaksdeltakelse.GirRett>()

    /** Radene med datoer som ikke kan danne en periode — til innsyn og varsling, aldri beregning. */
    val ugyldige: List<Tiltaksdeltakelse.Ugyldig> get() = deltakelser.filterIsInstance<Tiltaksdeltakelse.Ugyldig>()

    /** Radene der selve kildestatusen er en kode vi ikke kjenner igjen. */
    val medUkjentKildestatus: List<Tiltaksdeltakelse> get() = deltakelser.filter { it.kildestatus is Kildestatus.Ukjent }

    /** Alt som er ukjent på tvers av deltakelsene — status, tiltakskoder og Komet-årsaker — til varsling og visning. */
    val ukjenteKildeverdier: List<UkjentKildeverdi> get() = deltakelser.flatMap { it.ukjenteKildeverdier }

    /** Periodene kilden faktisk oppga — deltakelser uten sammenhengende datoer bidrar ikke. */
    val perioder: List<Periode> get() = deltakelser.mapNotNull { it.periodeFraKilden }

    /**
     * Perioden fra tidligste start til seneste slutt blant [perioder], eller `null` når ingen deltakelse har en periode.
     * Total utstrekning, ikke sammenhengende dekning — det kan være hull mellom deltakelsene.
     */
    val totalPeriode: Periode?
        get() = perioder.reduceOrNull { venstre, høyre ->
            Periode(
                fraOgMed = if (venstre.fraOgMed <= høyre.fraOgMed) venstre.fraOgMed else høyre.fraOgMed,
                tilOgMed = if (venstre.tilOgMed >= høyre.tilOgMed) venstre.tilOgMed else høyre.tilOgMed,
            )
        }

    /**
     * Deltakelsene som kan gjelde [periode]: [Overlapp.Ja] og [Overlapp.Kanskje].
     * [Overlapp.Kanskje] er med fordi fravær av datoer ikke er bevis på fravær av overlapp — kallere som bare vil ha bekreftet overlapp filtrerer selv med [overlapper].
     */
    fun overlappende(periode: Periode): Tiltaksdeltakelser =
        Tiltaksdeltakelser(deltakelser.filter { it.overlapper(periode) != Overlapp.Nei })
}
