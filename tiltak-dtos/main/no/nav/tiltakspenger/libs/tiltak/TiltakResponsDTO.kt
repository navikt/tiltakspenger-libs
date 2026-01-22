package no.nav.tiltakspenger.libs.tiltak

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.lang.RuntimeException
import java.time.LocalDate
import java.time.LocalDateTime

data class TiltakResponsDTO(
    val tiltak: List<TiltakDTO>? = null,
    val feil: FeilmeldingDTO? = null,
) {
    enum class FeilmeldingDTO(
        val melding: String,
    ) {
        UkjentFeil("Ukjent feil"),
    }

    data class TiltakDTO(
        val id: String,
        val gjennomforing: GjennomføringDTO,
        val deltakelseFom: LocalDate?,
        val deltakelseTom: LocalDate?,
        val deltakelseStatus: DeltakerStatusDTO,
        val deltakelseDagerUke: Float?,
        val deltakelseProsent: Float?,
        val kilde: String,
        val registrertDato: LocalDateTime,
    )

    data class GjennomføringDTO(
        val id: String,
        val arrangørnavn: String,
        val typeNavn: String,
        val arenaKode: TiltakType,
        val deltidsprosent: Double?,
    )

    enum class DeltakerStatusDTO(
        val status: String,
        val rettTilÅSøke: Boolean,
    ) {
        VENTER_PA_OPPSTART("Venter på oppstart", true),
        DELTAR("Deltar", true),
        HAR_SLUTTET("Har sluttet", true),
        AVBRUTT("Avbrutt", true),
        FULLFORT("Fullført", true),

        IKKE_AKTUELL("Ikke aktuell", false),
        FEILREGISTRERT("Feilregistrert", false),
        PABEGYNT_REGISTRERING("Påbegynt registrering", false),
        SOKT_INN("Søkt inn", false),
        VENTELISTE("Venteliste", false),
        VURDERES("Vurderes", false),
    }

    enum class Tiltaksgruppe(
        val navn: String,
    ) {
        AFT("Arbeidsforberedende trening"),
        AMB("Tiltak i arbeidsmarkedsbedrift"),
        ARBPRAKS("Arbeidspraksis"),
        ARBRREHAB("Arbeidsrettet rehabilitering"),
        ARBTREN("Arbeidstrening"),
        AVKLARING("Avklaring"),
        BEHPSSAM("Behandling - lettere psykiske/sammensatte lidelser"),
        ETAB("Egenetablering"),
        FORSOK("Forsøk"),
        LONNTILS("Lønnstilskudd"),
        OPPFOLG("Oppfølging"),
        OPPL("Opplæring"),
        TILRETTE("Tilrettelegging"),
        UTFAS("Tiltak under utfasing"),
        VARIGASV("Varig tilrettelagt arbeid"),
        JOBBSKAP("Jobbskapingsprosjekter"),
        BIO("Bedriftsintern opplæring (BIO)"),
        BISTAND("Arbeid med Bistand (AB)"),
        INST_S("Nye plasser institusjonelle tiltak"),
        MIDSYSS("Midlertidig sysselsetting"),
    }

    enum class TiltakType(
        val navn: String,
        val tiltaksgruppe: Tiltaksgruppe,
        val rettPåTiltakspenger: Boolean,
    ) {
        // gir rett
        ARBTREN("Arbeidstrening", Tiltaksgruppe.ARBTREN, true),
        GRUPPEAMO("Gruppe AMO", Tiltaksgruppe.OPPL, true),
        ENKELAMO("Enkeltplass AMO", Tiltaksgruppe.OPPL, true),
        GRUFAGYRKE("Gruppe Fag- og yrkesopplæring VGS og høyere yrkesfaglig utdanning", Tiltaksgruppe.OPPL, true),
        ENKFAGYRKE("Enkeltplass Fag- og yrkesopplæring VGS og høyere yrkesfaglig utdanning", Tiltaksgruppe.OPPL, true),
        HOYEREUTD("Høyere utdanning", Tiltaksgruppe.OPPL, true),
        ARBFORB("Arbeidsforberedende trening (AFT)", Tiltaksgruppe.AFT, true),
        UTVAOONAV("Arbeid med støtte", Tiltaksgruppe.FORSOK, true),
        FORSAMOGRU("Forsøk AMO gruppe", Tiltaksgruppe.FORSOK, true),
        FORSAMOENK("Forsøk AMO enkeltplass", Tiltaksgruppe.FORSOK, true),
        FORSFAGGRU("Forsøk fag- og yrkesopplæring gruppe", Tiltaksgruppe.FORSOK, true),
        FORSFAGENK("Forsøk fag- og yrkesopplæring enkeltplass", Tiltaksgruppe.FORSOK, true),
        FORSHOYUTD("Forsøk høyere utdanning", Tiltaksgruppe.FORSOK, true),
        FORSOPPLEV("Forsøk opplæringstiltak av lengre varighet", Tiltaksgruppe.FORSOK, true),
        AVKLARAG("Avklaring", Tiltaksgruppe.AVKLARING, true),
        ARBRRHDAG("Arbeidsrettet rehabilitering (dag)", Tiltaksgruppe.ARBRREHAB, true),
        JOBBK("Jobbklubb", Tiltaksgruppe.OPPFOLG, true),
        INDOPPFAG("Oppfølging", Tiltaksgruppe.OPPFOLG, true),
        INDJOBSTOT("Individuell jobbstøtte (IPS)", Tiltaksgruppe.OPPFOLG, true),
        DIGIOPPARB("Digitalt jobbsøkerkurs", Tiltaksgruppe.OPPFOLG, true),
        UTVOPPFOPL("Utvidet oppfølging i opplæring", Tiltaksgruppe.OPPFOLG, true),
        IPSUNG("Individuell karrierestøtte (IPS Ung)", Tiltaksgruppe.OPPFOLG, true),

        // nye tiltakskoder som ikke finnes i Arena, gir rett
        ARBEIDSMARKEDSOPPLAERING("Arbeidsmarkedsopplæring (AMO)", Tiltaksgruppe.OPPL, true),
        NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV("Norskopplæring, grunnleggende ferdigheter og FOV", Tiltaksgruppe.OPPL, true),
        STUDIESPESIALISERING("Studiespesialisering", Tiltaksgruppe.OPPL, true),
        FAG_OG_YRKESOPPLAERING("Fag- og yrkesopplæring", Tiltaksgruppe.OPPL, true),
        HOYERE_YRKESFAGLIG_UTDANNING("Høyere yrkesfaglig utdanning", Tiltaksgruppe.OPPL, true),

        // gir ikke rett
        REFINO("Resultatbasert finansiering av oppfølging", Tiltaksgruppe.FORSOK, false),
        MIDLONTIL("Midlertidig lønnstilskudd", Tiltaksgruppe.LONNTILS, false),
        VARLONTIL("Varig lønnstilskudd", Tiltaksgruppe.LONNTILS, false),
        TILSJOBB("Tilskudd til sommerjobb", Tiltaksgruppe.LONNTILS, false),
        ETAB("Egenetablering", Tiltaksgruppe.ETAB, false),
        VASV("Varig tilrettelagt arbeid i skjermet virksomhet", Tiltaksgruppe.VARIGASV, false),
        VATIAROR("Varig tilrettelagt arbeid i ordinær virksomhet", Tiltaksgruppe.VARIGASV, false),
        FUNKSJASS("Funksjonsassistanse", Tiltaksgruppe.TILRETTE, false),
        INKLUTILS("Inkluderingstilskudd", Tiltaksgruppe.TILRETTE, false),
        EKSPEBIST("Ekspertbistand", Tiltaksgruppe.TILRETTE, false),
        MENTOR("Mentor", Tiltaksgruppe.OPPFOLG, false),

        // utfases
        AMBF1("AMB Avklaring (fase 1)", Tiltaksgruppe.UTFAS, false),
        KURS("Andre kurs", Tiltaksgruppe.UTFAS, false),
        ANNUTDANN("Annen utdanning", Tiltaksgruppe.UTFAS, false),
        ABOPPF("Arbeid med bistand A oppfølging", Tiltaksgruppe.UTFAS, false),
        ABUOPPF("Arbeid med bistand A utvidet oppfølging", Tiltaksgruppe.UTFAS, false),
        ABIST("Arbeid med Bistand (AB)", Tiltaksgruppe.UTFAS, false),
        ABTBOPPF("Arbeid med bistand B", Tiltaksgruppe.UTFAS, false),
        AMO("Arbeidsmarkedsopplæring (AMO)", Tiltaksgruppe.UTFAS, false),
        AMOE("Arbeidsmarkedsopplæring (AMO) enkeltplass", Tiltaksgruppe.UTFAS, false),
        AMOB("Arbeidsmarkedsopplæring (AMO) i bedrift", Tiltaksgruppe.UTFAS, false),
        AMOY("Arbeidsmarkedsopplæring (AMO) yrkeshemmede", Tiltaksgruppe.UTFAS, false),
        PRAKSORD("Arbeidspraksis i ordinær virksomhet", Tiltaksgruppe.UTFAS, false),
        PRAKSKJERM("Arbeidspraksis i skjermet virksomhet", Tiltaksgruppe.UTFAS, false),
        ARBRRHBAG("Arbeidsrettet rehabilitering", Tiltaksgruppe.UTFAS, false),
        ARBRRHBSM("Arbeidsrettet rehabilitering - sykmeldt arbeidstaker", Tiltaksgruppe.UTFAS, false),
        ARBRDAGSM("Arbeidsrettet rehabilitering (dag) - sykmeldt arbeidstaker", Tiltaksgruppe.UTFAS, false),
        ARBRRDOGN("Arbeidsrettet rehabilitering (døgn)", Tiltaksgruppe.UTFAS, false),
        ARBDOGNSM("Arbeidsrettet rehabilitering (døgn) - sykmeldt arbeidstaker", Tiltaksgruppe.UTFAS, false),
        ASV("Arbeidssamvirke (ASV)", Tiltaksgruppe.UTFAS, false),
        ATG("Arbeidstreningsgrupper", Tiltaksgruppe.UTFAS, false),
        AVKLARUS("Avklaring", Tiltaksgruppe.UTFAS, false),
        AVKLARSP("Avklaring - sykmeldt arbeidstaker", Tiltaksgruppe.UTFAS, false),
        AVKLARKV("Avklaring av kortere varighet", Tiltaksgruppe.UTFAS, false),
        AVKLARSV("Avklaring i skjermet virksomhet", Tiltaksgruppe.UTFAS, false),
        BIA("Bedriftsintern attføring", Tiltaksgruppe.UTFAS, false),
        BREVKURS("Brevkurs", Tiltaksgruppe.UTFAS, false),
        DIVTILT("Diverse tiltak", Tiltaksgruppe.UTFAS, false),
        FLEKSJOBB("Fleksibel jobb - lønnstilskudd av lengre varighet", Tiltaksgruppe.UTFAS, false),
        TILRTILSK(
            "Forebyggings- og tilretteleggingstilskudd IA virksomheter og BHT-honorar",
            Tiltaksgruppe.UTFAS,
            false,
        ),
        KAT("Formidlingstjenester", Tiltaksgruppe.UTFAS, false),
        VALS("Formidlingstjenester - Ventelønn", Tiltaksgruppe.UTFAS, false),
        GRUNNSKOLE("Grunnskole", Tiltaksgruppe.UTFAS, false),
        HOYSKOLE("Høyskole/Universitet", Tiltaksgruppe.UTFAS, false),
        INDOPPFOLG("Individuelt oppfølgingstiltak", Tiltaksgruppe.UTFAS, false),
        ITGRTILS("Integreringstilskudd", Tiltaksgruppe.UTFAS, false),
        JOBBKLUBB("Intern jobbklubb", Tiltaksgruppe.UTFAS, false),
        JOBBFOKUS("Jobbfokus/Utvidet formidlingsbistand", Tiltaksgruppe.UTFAS, false),
        JOBBBONUS("Jobbklubb med bonusordning", Tiltaksgruppe.UTFAS, false),
        JOBBSKAP("Jobbskapingsprosjekter", Tiltaksgruppe.UTFAS, false),
        AMBF2("Kvalifisering i arbeidsmarkedsbedrift", Tiltaksgruppe.UTFAS, false),
        STATLAERL("Lærlinger i statlige etater", Tiltaksgruppe.UTFAS, false),
        LONNTILS("Lønnstilskudd", Tiltaksgruppe.UTFAS, false),
        REAKTUFOR("Lønnstilskudd - reaktivisering av uførepensjonister", Tiltaksgruppe.UTFAS, false),
        LONNTILL("Lønnstilskudd av lengre varighet", Tiltaksgruppe.UTFAS, false),
        NETTAMO("Nettbasert arbeidsmarkedsopplæring (AMO)", Tiltaksgruppe.UTFAS, false),
        NETTKURS("Nettkurs", Tiltaksgruppe.UTFAS, false),
        INST_S("Nye plasser institusjonelle tiltak", Tiltaksgruppe.UTFAS, false),
        INDOPPFSP("Oppfølging - sykmeldt arbeidstaker", Tiltaksgruppe.UTFAS, false),
        SYSSLANG("Sysselsettingstiltak for langtidsledige", Tiltaksgruppe.UTFAS, false),
        YHEMMOFF("Sysselsettingstiltak for yrkeshemmede", Tiltaksgruppe.UTFAS, false),
        SYSSOFF("Sysselsettingstiltak i offentlig sektor for yrkeshemmede", Tiltaksgruppe.UTFAS, false),
        LONNTIL("Tidsbegrenset lønnstilskudd", Tiltaksgruppe.UTFAS, false),
        TIDSUBLONN("Tidsubestemt lønnstilskudd", Tiltaksgruppe.UTFAS, false),
        AMBF3("Tilrettelagt arbeid i arbeidsmarkedsbedrift", Tiltaksgruppe.UTFAS, false),
        TILRETTEL("Tilrettelegging for arbeidstaker", Tiltaksgruppe.UTFAS, false),
        TILPERBED("Tilretteleggingstilskudd for arbeidssøker", Tiltaksgruppe.UTFAS, false),
        PV("Produksjonsverksted (PV)", Tiltaksgruppe.UTFAS, false),
        SPA("Spa prosjekter", Tiltaksgruppe.UTFAS, false),
        UFØREPENLØ("Uførepensjon som lønnstilskudd", Tiltaksgruppe.UTFAS, false),
        UTDYRK("Utdanning", Tiltaksgruppe.UTFAS, false),
        UTDPERMVIK("Utdanningspermisjoner", Tiltaksgruppe.UTFAS, false),
        VIKARBLED("Utdanningsvikariater", Tiltaksgruppe.UTFAS, false),
        UTBHLETTPS("Utredning/behandling lettere psykiske lidelser", Tiltaksgruppe.UTFAS, false),
        UTBHPSLD("Utredning/behandling lettere psykiske og sammensatte lidelser", Tiltaksgruppe.UTFAS, false),
        UTBHSAMLI("Utredning/behandling sammensatte lidelser", Tiltaksgruppe.UTFAS, false),
        VV("Varig vernet arbeid (VVA)", Tiltaksgruppe.UTFAS, false),
        VIDRSKOLE("Videregående skole", Tiltaksgruppe.UTFAS, false),
        OPPLT2AAR("2-årig opplæringstiltak", Tiltaksgruppe.UTFAS, false),

        // rariteter
        LONNTILAAP("Arbeidsavklaringspenger som lønnstilskudd", Tiltaksgruppe.FORSOK, false),
        BIO("Bedriftsintern opplæring (BIO)", Tiltaksgruppe.BIO, false),
        TESTING("Lenes testtiltak", Tiltaksgruppe.OPPFOLG, false),
        NYTEST("Nytt testtiltak", Tiltaksgruppe.ARBTREN, false),
        INDOPPRF("Resultatbasert finansiering av formidlingsbistand", Tiltaksgruppe.FORSOK, false),
        SUPPEMP("Supported Employment", Tiltaksgruppe.FORSOK, false),
    }

    enum class DeltakerStatusType(
        val navn: String,
        val girRettTilÅASøke: Boolean,
    ) {
        DELAVB("Deltakelse avbrutt", true),
        FULLF("Fullført", true),
        GJENN("Gjennomføres", true),
        GJENN_AVB("Gjennomføring avbrutt", true),
        IKKEM("Ikke møtt", true),
        JATAKK("Takket ja til tilbud", true),
        TILBUD("Godkjent tiltaksplass", true),

        AKTUELL("Aktuell", false),
        AVSLAG("Fått avslag", false),
        GJENN_AVL("Gjennomføring avlyst", false),
        IKKAKTUELL("Ikke aktuell", false),
        INFOMOETE("Informasjonsmøte", false),
        NEITAKK("Takket nei til tilbud", false),
        VENTELISTE("Venteliste", false),
    }
}

object TiltakstypeGirIkkeRett

fun TiltakResponsDTO.TiltakType.toTiltakstypeSomGirRett(): Either<TiltakstypeGirIkkeRett, TiltakstypeSomGirRett> =
    when (this) {
        TiltakResponsDTO.TiltakType.ARBEIDSMARKEDSOPPLAERING -> TiltakstypeSomGirRett.ARBEIDSMARKEDSOPPLAERING.right()
        TiltakResponsDTO.TiltakType.ARBFORB -> TiltakstypeSomGirRett.ARBEIDSFORBEREDENDE_TRENING.right()
        TiltakResponsDTO.TiltakType.ARBRRHDAG -> TiltakstypeSomGirRett.ARBEIDSRETTET_REHABILITERING.right()
        TiltakResponsDTO.TiltakType.ARBTREN -> TiltakstypeSomGirRett.ARBEIDSTRENING.right()
        TiltakResponsDTO.TiltakType.AVKLARAG -> TiltakstypeSomGirRett.AVKLARING.right()
        TiltakResponsDTO.TiltakType.DIGIOPPARB -> TiltakstypeSomGirRett.DIGITAL_JOBBKLUBB.right()
        TiltakResponsDTO.TiltakType.ENKELAMO -> TiltakstypeSomGirRett.ENKELTPLASS_AMO.right()
        TiltakResponsDTO.TiltakType.ENKFAGYRKE -> TiltakstypeSomGirRett.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG.right()
        TiltakResponsDTO.TiltakType.FAG_OG_YRKESOPPLAERING -> TiltakstypeSomGirRett.FAG_OG_YRKESOPPLAERING.right()
        TiltakResponsDTO.TiltakType.FORSOPPLEV -> TiltakstypeSomGirRett.FORSØK_OPPLÆRING_LENGRE_VARIGHET.right()
        TiltakResponsDTO.TiltakType.GRUPPEAMO -> TiltakstypeSomGirRett.GRUPPE_AMO.right()
        TiltakResponsDTO.TiltakType.GRUFAGYRKE -> TiltakstypeSomGirRett.GRUPPE_VGS_OG_HØYERE_YRKESFAG.right()
        TiltakResponsDTO.TiltakType.HOYEREUTD -> TiltakstypeSomGirRett.HØYERE_UTDANNING.right()
        TiltakResponsDTO.TiltakType.HOYERE_YRKESFAGLIG_UTDANNING -> TiltakstypeSomGirRett.HOYERE_YRKESFAGLIG_UTDANNING.right()
        TiltakResponsDTO.TiltakType.INDJOBSTOT -> TiltakstypeSomGirRett.INDIVIDUELL_JOBBSTØTTE.right()
        TiltakResponsDTO.TiltakType.IPSUNG -> TiltakstypeSomGirRett.INDIVIDUELL_KARRIERESTØTTE_UNG.right()
        TiltakResponsDTO.TiltakType.JOBBK -> TiltakstypeSomGirRett.JOBBKLUBB.right()
        TiltakResponsDTO.TiltakType.INDOPPFAG -> TiltakstypeSomGirRett.OPPFØLGING.right()
        TiltakResponsDTO.TiltakType.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> TiltakstypeSomGirRett.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV.right()
        TiltakResponsDTO.TiltakType.STUDIESPESIALISERING -> TiltakstypeSomGirRett.STUDIESPESIALISERING.right()
        TiltakResponsDTO.TiltakType.UTVAOONAV -> TiltakstypeSomGirRett.UTVIDET_OPPFØLGING_I_NAV.right()
        TiltakResponsDTO.TiltakType.UTVOPPFOPL -> TiltakstypeSomGirRett.UTVIDET_OPPFØLGING_I_OPPLÆRING.right()

        TiltakResponsDTO.TiltakType.REFINO,
        TiltakResponsDTO.TiltakType.MIDLONTIL,
        TiltakResponsDTO.TiltakType.VARLONTIL,
        TiltakResponsDTO.TiltakType.TILSJOBB,
        TiltakResponsDTO.TiltakType.ETAB,
        TiltakResponsDTO.TiltakType.VASV,
        TiltakResponsDTO.TiltakType.VATIAROR,
        TiltakResponsDTO.TiltakType.FUNKSJASS,
        TiltakResponsDTO.TiltakType.INKLUTILS,
        TiltakResponsDTO.TiltakType.EKSPEBIST,
        TiltakResponsDTO.TiltakType.MENTOR,
        TiltakResponsDTO.TiltakType.AMBF1,
        TiltakResponsDTO.TiltakType.KURS,
        TiltakResponsDTO.TiltakType.ANNUTDANN,
        TiltakResponsDTO.TiltakType.ABOPPF,
        TiltakResponsDTO.TiltakType.ABUOPPF,
        TiltakResponsDTO.TiltakType.ABIST,
        TiltakResponsDTO.TiltakType.ABTBOPPF,
        TiltakResponsDTO.TiltakType.AMO,
        TiltakResponsDTO.TiltakType.AMOE,
        TiltakResponsDTO.TiltakType.AMOB,
        TiltakResponsDTO.TiltakType.AMOY,
        TiltakResponsDTO.TiltakType.PRAKSORD,
        TiltakResponsDTO.TiltakType.PRAKSKJERM,
        TiltakResponsDTO.TiltakType.ARBRRHBAG,
        TiltakResponsDTO.TiltakType.ARBRRHBSM,
        TiltakResponsDTO.TiltakType.ARBRDAGSM,
        TiltakResponsDTO.TiltakType.ARBRRDOGN,
        TiltakResponsDTO.TiltakType.ARBDOGNSM,
        TiltakResponsDTO.TiltakType.ASV,
        TiltakResponsDTO.TiltakType.ATG,
        TiltakResponsDTO.TiltakType.AVKLARUS,
        TiltakResponsDTO.TiltakType.AVKLARSP,
        TiltakResponsDTO.TiltakType.AVKLARKV,
        TiltakResponsDTO.TiltakType.AVKLARSV,
        TiltakResponsDTO.TiltakType.BIA,
        TiltakResponsDTO.TiltakType.BREVKURS,
        TiltakResponsDTO.TiltakType.DIVTILT,
        TiltakResponsDTO.TiltakType.FLEKSJOBB,
        TiltakResponsDTO.TiltakType.TILRTILSK,
        TiltakResponsDTO.TiltakType.KAT,
        TiltakResponsDTO.TiltakType.VALS,
        TiltakResponsDTO.TiltakType.GRUNNSKOLE,
        TiltakResponsDTO.TiltakType.HOYSKOLE,
        TiltakResponsDTO.TiltakType.INDOPPFOLG,
        TiltakResponsDTO.TiltakType.ITGRTILS,
        TiltakResponsDTO.TiltakType.JOBBKLUBB,
        TiltakResponsDTO.TiltakType.JOBBFOKUS,
        TiltakResponsDTO.TiltakType.JOBBBONUS,
        TiltakResponsDTO.TiltakType.JOBBSKAP,
        TiltakResponsDTO.TiltakType.AMBF2,
        TiltakResponsDTO.TiltakType.STATLAERL,
        TiltakResponsDTO.TiltakType.LONNTILS,
        TiltakResponsDTO.TiltakType.REAKTUFOR,
        TiltakResponsDTO.TiltakType.LONNTILL,
        TiltakResponsDTO.TiltakType.NETTAMO,
        TiltakResponsDTO.TiltakType.NETTKURS,
        TiltakResponsDTO.TiltakType.INST_S,
        TiltakResponsDTO.TiltakType.INDOPPFSP,
        TiltakResponsDTO.TiltakType.SYSSLANG,
        TiltakResponsDTO.TiltakType.YHEMMOFF,
        TiltakResponsDTO.TiltakType.SYSSOFF,
        TiltakResponsDTO.TiltakType.LONNTIL,
        TiltakResponsDTO.TiltakType.TIDSUBLONN,
        TiltakResponsDTO.TiltakType.AMBF3,
        TiltakResponsDTO.TiltakType.TILRETTEL,
        TiltakResponsDTO.TiltakType.TILPERBED,
        TiltakResponsDTO.TiltakType.PV,
        TiltakResponsDTO.TiltakType.SPA,
        TiltakResponsDTO.TiltakType.UFØREPENLØ,
        TiltakResponsDTO.TiltakType.UTDYRK,
        TiltakResponsDTO.TiltakType.UTDPERMVIK,
        TiltakResponsDTO.TiltakType.VIKARBLED,
        TiltakResponsDTO.TiltakType.UTBHLETTPS,
        TiltakResponsDTO.TiltakType.UTBHPSLD,
        TiltakResponsDTO.TiltakType.UTBHSAMLI,
        TiltakResponsDTO.TiltakType.VV,
        TiltakResponsDTO.TiltakType.VIDRSKOLE,
        TiltakResponsDTO.TiltakType.OPPLT2AAR,
        TiltakResponsDTO.TiltakType.LONNTILAAP,
        TiltakResponsDTO.TiltakType.BIO,
        TiltakResponsDTO.TiltakType.TESTING,
        TiltakResponsDTO.TiltakType.NYTEST,
        TiltakResponsDTO.TiltakType.INDOPPRF,
        TiltakResponsDTO.TiltakType.SUPPEMP,
        // Disse har gitt rett tidligere, men er ikke aktuelle mer.
        // Behandler dem som om de ikke gir rett fordi det ikke finnes utbetalingskoder for dem.
        TiltakResponsDTO.TiltakType.FORSAMOGRU,
        TiltakResponsDTO.TiltakType.FORSAMOENK,
        TiltakResponsDTO.TiltakType.FORSFAGGRU,
        TiltakResponsDTO.TiltakType.FORSFAGENK,
        TiltakResponsDTO.TiltakType.FORSHOYUTD,
        -> TiltakstypeGirIkkeRett.left()
    }
