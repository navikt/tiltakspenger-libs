package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import no.nav.tiltakspenger.libs.common.personopplysning.Tilknytningstittel
import no.nav.tiltakspenger.libs.periode.Overlapp
import no.nav.tiltakspenger.libs.periode.Periode
import no.nav.tiltakspenger.libs.periode.ÅpenPeriode
import java.time.LocalDate

/**
 * Én tiltaksdeltakelse, slik kilden oppga den.
 *
 * Typen er en **saksopplysning**: den beskriver hva kilden sa, og skal bevares uendret.
 * Den sier ingenting om utfallet av en behandling.
 * Om noe kan innvilges avhenger også av saksbehandlers vurdering, som libs ikke kjenner — derfor finnes ikke ordet «kanInnvilges» her.
 *
 * Varianten uttrykker **datakvalitet**, ikke utfall.
 * Vi filtrerer ikke bort noe på vei inn; alt kilden ga oss får en variant, og konsumenten velger hva den vil bruke.
 * En deltakelse med tiltakstype vi ikke kjenner, eller med datoer som ikke henger sammen, forsvinner ikke i stillhet slik den gjorde før.
 *
 * **Ikke forgren på varianten for å avgjøre et utfall.**
 * Varianten beskriver kilden, og saksbehandler kan senere vurdere noe annet enn det kilden sa.
 * Skriver du `when (deltakelse) { is GirRett.MedPeriode -> beregn(deltakelse) ... }`, tvinger du kallere som har en vurdert periode til enten å fabrikkere en falsk kildeverdi eller duplisere logikken.
 * Beslutningsstøtte skal ta verdiene den trenger (tiltakstype, periode, omfang), ikke aggregatet.
 */
sealed interface Tiltaksdeltakelse {
    /** Kildesystemets id for deltakelsen. */
    val id: EksternDeltakelseId

    /**
     * Kildens egen status, bevart ordrett.
     * [Tiltakskilde] utledes herfra.
     * Kan være en [Kildestatus.Ukjent]: også en kode vi ikke kjenner igjen bæres, og blokkerer tolkning til den er mappet.
     */
    val kildestatus: Kildestatus

    /**
     * Navnet på tiltakstypen, fritekst fra kilden, som «Oppfølging».
     * Dette er ikke stedsinformasjon, og er derfor det som kan vises når arrangøren ikke skal røpes.
     */
    val tiltakstypenavn: String

    /**
     * Tiltakskoden slik kilden oppga den, for eksempel Arenas `ARBFORB`.
     * Kun til visning og gjenkjenning — se [Tiltakstype.tiltakskodeFraKilden].
     */
    val tiltakskodeFraKilden: String

    /**
     * Kildens leselige tittel, typisk «\<type\> hos \<arrangør\>».
     * Inneholder arrangøren, og er derfor stedsinformasjon.
     * Kilden er fritekst uten garanti mot tomt innhold, så tittelen kan mangle — `null` betyr at kilden ikke ga noen.
     * Visning faller da tilbake på [tiltakstypenavn], samme gren som ved adressebeskyttelse.
     */
    val tittel: Tilknytningstittel?

    val arrangør: Arrangør

    val omfang: Deltakelsesomfang

    /**
     * Datoene kilden oppga, der hver av dem kan mangle.
     * `null` kun for [Ugyldig], der datoene ikke henger sammen og derfor ikke kan danne en [ÅpenPeriode].
     */
    val åpenPeriode: ÅpenPeriode?

    /**
     * Startdato hos kilden.
     * Mangler ofte, særlig når deltakeren venter på oppstart.
     */
    val fraOgMed: LocalDate?

    /**
     * Sluttdato hos kilden.
     * Mangler ofte.
     */
    val tilOgMed: LocalDate?

    /**
     * Kilden oppgir denne for Arena og Komet; Team Tiltak har ingen gjennomføring.
     * `null` når den mangler — aldri tom streng.
     */
    val gjennomføringId: GjennomføringId?

    /**
     * Tiltakstypen er kjent og gir rett til tiltakspenger.
     *
     * Delt i to etter om kilden ga oss begge datoene, slik at [MedPeriode] har en total [MedPeriode.periode] og slipper nullsjekker der det betyr noe.
     * Splitten er en påstand om kildedata, ikke om utfall.
     */
    sealed interface GirRett : Tiltaksdeltakelse {
        val tiltakstype: TiltakstypeSomGirRett

        override val åpenPeriode: ÅpenPeriode

        /** Kilden ga både start- og sluttdato, og de henger sammen. */
        data class MedPeriode(
            override val id: EksternDeltakelseId,
            override val kildestatus: Kildestatus,
            override val tiltakstype: TiltakstypeSomGirRett,
            override val tiltakstypenavn: String,
            override val tiltakskodeFraKilden: String,
            override val tittel: Tilknytningstittel?,
            override val arrangør: Arrangør,
            override val omfang: Deltakelsesomfang,
            override val gjennomføringId: GjennomføringId?,
            val periode: Periode,
        ) : GirRett {
            override val åpenPeriode: ÅpenPeriode = ÅpenPeriode(periode.fraOgMed, periode.tilOgMed)
            override val fraOgMed: LocalDate = periode.fraOgMed
            override val tilOgMed: LocalDate = periode.tilOgMed
        }

        /** Kilden manglet én eller begge datoene. */
        data class UtenPeriode(
            override val id: EksternDeltakelseId,
            override val kildestatus: Kildestatus,
            override val tiltakstype: TiltakstypeSomGirRett,
            override val tiltakstypenavn: String,
            override val tiltakskodeFraKilden: String,
            override val tittel: Tilknytningstittel?,
            override val arrangør: Arrangør,
            override val omfang: Deltakelsesomfang,
            override val gjennomføringId: GjennomføringId?,
            override val åpenPeriode: ÅpenPeriode,
        ) : GirRett {
            override val fraOgMed: LocalDate? get() = åpenPeriode.fraOgMed
            override val tilOgMed: LocalDate? get() = åpenPeriode.tilOgMed

            init {
                require(åpenPeriode.periode == null) { "UtenPeriode krever at minst én av datoene mangler — med begge på plass er det MedPeriode som gjelder" }
            }
        }
    }

    /**
     * Tiltakstypen er kjent, men gir ikke rett til tiltakspenger.
     *
     * Koden bæres som `String` fordi vi aldri diskriminerer på den.
     * Deltakelsen flyter likevel inn: saksbehandler skal kunne se den, og den er ofte begrunnelsen for et avslag.
     */
    data class GirIkkeRett(
        override val id: EksternDeltakelseId,
        override val kildestatus: Kildestatus,
        override val tiltakstypenavn: String,
        override val tiltakskodeFraKilden: String,
        override val tittel: Tilknytningstittel?,
        override val arrangør: Arrangør,
        override val omfang: Deltakelsesomfang,
        override val gjennomføringId: GjennomføringId?,
        override val åpenPeriode: ÅpenPeriode,
    ) : Tiltaksdeltakelse {
        override val fraOgMed: LocalDate? get() = åpenPeriode.fraOgMed
        override val tilOgMed: LocalDate? get() = åpenPeriode.tilOgMed
    }

    /**
     * Tiltakskoden er ikke i tabellene våre.
     *
     * Før tok en ukjent Arena-kode ned hele oppslaget, fordi mappingen gjorde `valueOf` på fritekst.
     * Nå flyter den inn, og kan varsles på uten at noen mister tiltakshistorikken sin.
     */
    data class UkjentTiltakstype(
        override val id: EksternDeltakelseId,
        override val kildestatus: Kildestatus,
        override val tiltakstypenavn: String,
        override val tiltakskodeFraKilden: String,
        override val tittel: Tilknytningstittel?,
        override val arrangør: Arrangør,
        override val omfang: Deltakelsesomfang,
        override val gjennomføringId: GjennomføringId?,
        override val åpenPeriode: ÅpenPeriode,
    ) : Tiltaksdeltakelse {
        override val fraOgMed: LocalDate? get() = åpenPeriode.fraOgMed
        override val tilOgMed: LocalDate? get() = åpenPeriode.tilOgMed
    }

    /**
     * Datoene fra kilden kan ikke danne en periode — se [grunn].
     *
     * Disse ble tidligere silt bort i stillhet, slik at ingen oppdaget at kilden hadde korrupte rader.
     * Nå bæres de, slik at de kan vises og varsles på — men de skal aldri brukes som grunnlag for beregning.
     *
     * Varianten bærer [tiltakstype] — klassifiseringen vår — slik at det synes om en korrupt rad ville gitt rett.
     * Det er nettopp denne bøtta som skal varsles på, og «denne ville gitt rett» er det mest handlingsutløsende vi vet om en rad.
     */
    data class Ugyldig(
        override val id: EksternDeltakelseId,
        override val kildestatus: Kildestatus,
        override val tiltakstypenavn: String,
        val tiltakstype: Tiltakstype,
        override val tittel: Tilknytningstittel?,
        override val arrangør: Arrangør,
        override val omfang: Deltakelsesomfang,
        override val gjennomføringId: GjennomføringId?,
        override val fraOgMed: LocalDate,
        override val tilOgMed: LocalDate,
        val grunn: Ugyldiggrunn,
    ) : Tiltaksdeltakelse {
        override val tiltakskodeFraKilden: String get() = tiltakstype.tiltakskodeFraKilden

        /** Datoene henger ikke sammen, og kan derfor ikke danne en [ÅpenPeriode]. */
        override val åpenPeriode: ÅpenPeriode? get() = null

        init {
            when (grunn) {
                Ugyldiggrunn.SluttFørStart -> require(fraOgMed.isAfter(tilOgMed)) { "SluttFørStart krever at sluttdatoen ligger før startdatoen" }
                Ugyldiggrunn.DatoPåYttergrense -> require(fraOgMed == LocalDate.MAX || tilOgMed == LocalDate.MIN) { "DatoPåYttergrense krever LocalDate.MAX som startdato eller LocalDate.MIN som sluttdato" }
            }
        }
    }
}

/**
 * Hvorfor kildedataen ikke kan danne en periode.
 */
enum class Ugyldiggrunn {
    /** Sluttdatoen ligger før startdatoen. */
    SluttFørStart,

    /** Startdatoen er `LocalDate.MAX` eller sluttdatoen er `LocalDate.MIN` — tekniske yttergrenser som ikke kan danne en periode. */
    DatoPåYttergrense,
}

/**
 * Kildesystemet deltakelsen kommer fra.
 *
 * Utledet fra [Tiltaksdeltakelse.kildestatus], slik at kilde og status ikke kan komme i utakt.
 */
val Tiltaksdeltakelse.kilde: Tiltakskilde get() = kildestatus.kilde

/**
 * Perioden kilden oppga, eller `null` når en av datoene mangler eller de ikke henger sammen.
 * Datoer på tekniske yttergrenser (`LocalDate.MAX` som start, `LocalDate.MIN` som slutt) gir også `null`, siden [Periode] ikke kan bære dem.
 *
 * Virker på alle varianter, slik at kallere slipper å narrowe først.
 * Merk at en vurdert periode kan avvike fra denne; da er det den vurderte som gjelder for utfallet, og den eier konsumenten.
 */
val Tiltaksdeltakelse.periodeFraKilden: Periode? get() = åpenPeriode?.periode

/**
 * Om deltakelsen overlapper med [periode], så langt kildedataene rekker.
 *
 * Svaret er det samme som [ÅpenPeriode.overlapperMed] gir for datoene kilden oppga.
 * Har kilden bare én dato, bekrefter den overlapp når den ligger i [periode], og avkrefter når den ligger på feil side.
 * En [Tiltaksdeltakelse.Ugyldig] svarer alltid [Overlapp.Kanskje]: datoene henger ikke sammen, og da vet vi ingenting.
 */
fun Tiltaksdeltakelse.overlapper(periode: Periode): Overlapp {
    val åpenPeriode = åpenPeriode ?: return Overlapp.Kanskje
    return åpenPeriode.overlapperMed(periode)
}

/**
 * Bygger den varianten kildedataen faktisk kvalifiserer til.
 *
 * Funksjonen er total: den kaster ikke, og hver kombinasjon av inndata gir en variant.
 * Regelen bak er at en total funksjon bare kan kalle en konstruktør hvis invarianter den allerede har etablert — datovilkårene her speiler init-kravene i variantene og i [Periode].
 * Klassifiseringsregelen bor dermed ett sted, i domenet, i stedet for å bli gjentatt av hver kilde i infrastrukturen.
 *
 * Rekkefølgen er bevisst: ugyldige datoer slår ut først, uansett hvor fin tiltakstypen er.
 * En deltakelse med sluttdato før startdato er korrupt hos kilden, og da hjelper det ikke at typen gir rett.
 */
fun tiltaksdeltakelse(
    id: EksternDeltakelseId,
    kildestatus: Kildestatus,
    tiltakstype: Tiltakstype,
    tiltakstypenavn: String,
    tittel: Tilknytningstittel?,
    arrangør: Arrangør,
    omfang: Deltakelsesomfang,
    fraOgMed: LocalDate?,
    tilOgMed: LocalDate?,
    gjennomføringId: GjennomføringId?,
): Tiltaksdeltakelse {
    if (fraOgMed != null && tilOgMed != null) {
        val grunn = when {
            fraOgMed.isAfter(tilOgMed) -> Ugyldiggrunn.SluttFørStart
            fraOgMed == LocalDate.MAX || tilOgMed == LocalDate.MIN -> Ugyldiggrunn.DatoPåYttergrense
            else -> null
        }
        if (grunn != null) {
            return Tiltaksdeltakelse.Ugyldig(
                id = id,
                kildestatus = kildestatus,
                tiltakstypenavn = tiltakstypenavn,
                tiltakstype = tiltakstype,
                tittel = tittel,
                arrangør = arrangør,
                omfang = omfang,
                gjennomføringId = gjennomføringId,
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                grunn = grunn,
            )
        }
    }

    return when (tiltakstype) {
        is Tiltakstype.SomGirRett ->
            if (fraOgMed != null && tilOgMed != null) {
                Tiltaksdeltakelse.GirRett.MedPeriode(
                    id = id,
                    kildestatus = kildestatus,
                    tiltakstype = tiltakstype.tiltakstype,
                    tiltakstypenavn = tiltakstypenavn,
                    tiltakskodeFraKilden = tiltakstype.tiltakskodeFraKilden,
                    tittel = tittel,
                    arrangør = arrangør,
                    omfang = omfang,
                    gjennomføringId = gjennomføringId,
                    periode = Periode(fraOgMed, tilOgMed),
                )
            } else {
                Tiltaksdeltakelse.GirRett.UtenPeriode(
                    id = id,
                    kildestatus = kildestatus,
                    tiltakstype = tiltakstype.tiltakstype,
                    tiltakstypenavn = tiltakstypenavn,
                    tiltakskodeFraKilden = tiltakstype.tiltakskodeFraKilden,
                    tittel = tittel,
                    arrangør = arrangør,
                    omfang = omfang,
                    gjennomføringId = gjennomføringId,
                    åpenPeriode = ÅpenPeriode(fraOgMed, tilOgMed),
                )
            }

        is Tiltakstype.SomIkkeGirRett ->
            Tiltaksdeltakelse.GirIkkeRett(
                id = id,
                kildestatus = kildestatus,
                tiltakstypenavn = tiltakstypenavn,
                tiltakskodeFraKilden = tiltakstype.tiltakskodeFraKilden,
                tittel = tittel,
                arrangør = arrangør,
                omfang = omfang,
                gjennomføringId = gjennomføringId,
                åpenPeriode = ÅpenPeriode(fraOgMed, tilOgMed),
            )

        is Tiltakstype.Ukjent ->
            Tiltaksdeltakelse.UkjentTiltakstype(
                id = id,
                kildestatus = kildestatus,
                tiltakstypenavn = tiltakstypenavn,
                tiltakskodeFraKilden = tiltakstype.tiltakskodeFraKilden,
                tittel = tittel,
                arrangør = arrangør,
                omfang = omfang,
                gjennomføringId = gjennomføringId,
                åpenPeriode = ÅpenPeriode(fraOgMed, tilOgMed),
            )
    }
}
