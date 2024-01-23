package no.nav.tiltakspenger.libs.tiltak

import java.time.LocalDate
import java.time.LocalDateTime

data class TiltakResponsDTO(
    val tiltak: List<TiltakDTO>? = null,
    val feil: FeilmeldingDTO? = null,
) {

    enum class FeilmeldingDTO(val melding: String) {
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
        val fom: LocalDate?,
        val tom: LocalDate?,
    )

    enum class DeltakerStatusDTO(val status: String, val rettTilÅSøke: Boolean) {
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

    enum class Tiltaksgruppe(val navn: String) {
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

    enum class TiltakType(val navn: String, val tiltaksgruppe: Tiltaksgruppe, val rettPåTiltakspenger: Boolean) {
        AMBF1("AMB Avklaring (fase 1)", Tiltaksgruppe.UTFAS, true),
        KURS("Andre kurs", Tiltaksgruppe.UTFAS, false),
        ANNUTDANN("Annen utdanning", Tiltaksgruppe.UTFAS, false),
        ABOPPF("Arbeid med bistand A oppfølging", Tiltaksgruppe.UTFAS, true),
        ABUOPPF("Arbeid med bistand A utvidet oppfølging", Tiltaksgruppe.UTFAS, true),
        ABIST("Arbeid med Bistand (AB)", Tiltaksgruppe.UTFAS, true),
        ABTBOPPF("Arbeid med bistand B", Tiltaksgruppe.UTFAS, true),
        LONNTILAAP("Arbeidsavklaringspenger som lønnstilskudd", Tiltaksgruppe.FORSOK, false),
        ARBFORB("Arbeidsforberedende trening (AFT)", Tiltaksgruppe.AFT, true),
        AMO("Arbeidsmarkedsopplæring (AMO)", Tiltaksgruppe.UTFAS, true),
        AMOE("Arbeidsmarkedsopplæring (AMO) enkeltplass", Tiltaksgruppe.UTFAS, true),
        AMOB("Arbeidsmarkedsopplæring (AMO) i bedrift", Tiltaksgruppe.UTFAS, true),
        AMOY("Arbeidsmarkedsopplæring (AMO) yrkeshemmede", Tiltaksgruppe.UTFAS, true),
        PRAKSORD("Arbeidspraksis i ordinær virksomhet", Tiltaksgruppe.UTFAS, true),
        PRAKSKJERM("Arbeidspraksis i skjermet virksomhet", Tiltaksgruppe.UTFAS, true),
        ARBRRHBAG("Arbeidsrettet rehabilitering", Tiltaksgruppe.UTFAS, true),
        ARBRRHBSM("Arbeidsrettet rehabilitering - sykmeldt arbeidstaker", Tiltaksgruppe.UTFAS, true),
        ARBRRHDAG("Arbeidsrettet rehabilitering (dag)", Tiltaksgruppe.ARBRREHAB, true),
        ARBRDAGSM("Arbeidsrettet rehabilitering (dag) - sykmeldt arbeidstaker", Tiltaksgruppe.UTFAS, true),
        ARBRRDOGN("Arbeidsrettet rehabilitering (døgn)", Tiltaksgruppe.UTFAS, true),
        ARBDOGNSM("Arbeidsrettet rehabilitering (døgn) - sykmeldt arbeidstaker", Tiltaksgruppe.UTFAS, true),
        ASV("Arbeidssamvirke (ASV)", Tiltaksgruppe.UTFAS, false),
        ARBTREN("Arbeidstrening", Tiltaksgruppe.ARBTREN, true),
        ATG("Arbeidstreningsgrupper", Tiltaksgruppe.UTFAS, false),
        AVKLARAG("Avklaring", Tiltaksgruppe.AVKLARING, true),
        AVKLARUS("Avklaring", Tiltaksgruppe.UTFAS, true),
        AVKLARSP("Avklaring - sykmeldt arbeidstaker", Tiltaksgruppe.UTFAS, true),
        AVKLARKV("Avklaring av kortere varighet", Tiltaksgruppe.UTFAS, true),
        AVKLARSV("Avklaring i skjermet virksomhet", Tiltaksgruppe.UTFAS, true),
        BIA("Bedriftsintern attføring", Tiltaksgruppe.UTFAS, false),
        BIO("Bedriftsintern opplæring (BIO)", Tiltaksgruppe.BIO, false),
        BREVKURS("Brevkurs", Tiltaksgruppe.UTFAS, false),
        DIGIOPPARB("Digitalt oppfølgingstiltak for arbeidsledige (jobbklubb)", Tiltaksgruppe.OPPFOLG, true),
        DIVTILT("Diverse tiltak", Tiltaksgruppe.UTFAS, false),
        ETAB("Egenetablering", Tiltaksgruppe.ETAB, false),
        EKSPEBIST("Ekspertbistand", Tiltaksgruppe.TILRETTE, false),
        ENKELAMO("Enkeltplass AMO", Tiltaksgruppe.OPPL, true),
        ENKFAGYRKE("Enkeltplass Fag- og yrkesopplæring VGS og høyere yrkesfaglig utdanning", Tiltaksgruppe.OPPL, true),
        FLEKSJOBB("Fleksibel jobb - lønnstilskudd av lengre varighet", Tiltaksgruppe.UTFAS, false),
        TILRTILSK(
            "Forebyggings- og tilretteleggingstilskudd IA virksomheter og BHT-honorar",
            Tiltaksgruppe.UTFAS,
            false,
        ),
        KAT("Formidlingstjenester", Tiltaksgruppe.UTFAS, true),
        VALS("Formidlingstjenester - Ventelønn", Tiltaksgruppe.UTFAS, true),
        FORSAMOENK("Forsøk AMO enkeltplass", Tiltaksgruppe.FORSOK, true),
        FORSAMOGRU("Forsøk AMO gruppe", Tiltaksgruppe.FORSOK, false),
        FORSFAGENK("Forsøk fag- og yrkesopplæring enkeltplass", Tiltaksgruppe.FORSOK, true),
        FORSFAGGRU("Forsøk fag- og yrkesopplæring gruppe", Tiltaksgruppe.FORSOK, false),
        FORSHOYUTD("Forsøk høyere utdanning", Tiltaksgruppe.FORSOK, true),
        FUNKSJASS("Funksjonsassistanse", Tiltaksgruppe.TILRETTE, true),
        GRUNNSKOLE("Grunnskole", Tiltaksgruppe.UTFAS, false),
        GRUPPEAMO("Gruppe AMO", Tiltaksgruppe.OPPL, true),
        GRUFAGYRKE("Gruppe Fag- og yrkesopplæring VGS og høyere yrkesfaglig utdanning", Tiltaksgruppe.OPPL, true),
        HOYEREUTD("Høyere utdanning", Tiltaksgruppe.OPPL, true),
        HOYSKOLE("Høyskole/Universitet", Tiltaksgruppe.UTFAS, false),
        INDJOBSTOT("Individuell jobbstøtte (IPS)", Tiltaksgruppe.OPPFOLG, true),
        IPSUNG("Individuell karrierestøtte (IPS Ung)", Tiltaksgruppe.OPPFOLG, true),
        INDOPPFOLG("Individuelt oppfølgingstiltak", Tiltaksgruppe.UTFAS, true),
        INKLUTILS("Inkluderingstilskudd", Tiltaksgruppe.TILRETTE, true),
        ITGRTILS("Integreringstilskudd", Tiltaksgruppe.UTFAS, false),
        JOBBKLUBB("Intern jobbklubb", Tiltaksgruppe.UTFAS, true),
        JOBBFOKUS("Jobbfokus/Utvidet formidlingsbistand", Tiltaksgruppe.UTFAS, true),
        JOBBK("Jobbklubb", Tiltaksgruppe.OPPFOLG, true),
        JOBBBONUS("Jobbklubb med bonusordning", Tiltaksgruppe.UTFAS, true),
        JOBBSKAP("Jobbskapingsprosjekter", Tiltaksgruppe.UTFAS, false),
        AMBF2("Kvalifisering i arbeidsmarkedsbedrift", Tiltaksgruppe.UTFAS, false),
        TESTING("Lenes testtiltak", Tiltaksgruppe.OPPFOLG, false),
        STATLAERL("Lærlinger i statlige etater", Tiltaksgruppe.UTFAS, false),
        LONNTILS("Lønnstilskudd", Tiltaksgruppe.UTFAS, false),
        REAKTUFOR("Lønnstilskudd - reaktivisering av uførepensjonister", Tiltaksgruppe.UTFAS, false),
        LONNTILL("Lønnstilskudd av lengre varighet", Tiltaksgruppe.UTFAS, false),
        MENTOR("Mentor", Tiltaksgruppe.OPPFOLG, true),
        MIDLONTIL("Midlertidig lønnstilskudd", Tiltaksgruppe.LONNTILS, false),
        NETTAMO("Nettbasert arbeidsmarkedsopplæring (AMO)", Tiltaksgruppe.UTFAS, true),
        NETTKURS("Nettkurs", Tiltaksgruppe.UTFAS, false),
        INST_S("Nye plasser institusjonelle tiltak", Tiltaksgruppe.UTFAS, false),
        NYTEST("Nytt testtiltak", Tiltaksgruppe.ARBTREN, false),
        INDOPPFAG("Oppfølging", Tiltaksgruppe.OPPFOLG, true),
        INDOPPFSP("Oppfølging - sykmeldt arbeidstaker", Tiltaksgruppe.UTFAS, true),
        PV("Produksjonsverksted (PV)", Tiltaksgruppe.UTFAS, false),
        INDOPPRF("Resultatbasert finansiering av formidlingsbistand", Tiltaksgruppe.FORSOK, true),
        REFINO("Resultatbasert finansiering av oppfølging", Tiltaksgruppe.FORSOK, true),
        SPA("Spa prosjekter", Tiltaksgruppe.UTFAS, true),
        SUPPEMP("Supported Employment", Tiltaksgruppe.FORSOK, true),
        SYSSLANG("Sysselsettingstiltak for langtidsledige", Tiltaksgruppe.UTFAS, false),
        YHEMMOFF("Sysselsettingstiltak for yrkeshemmede", Tiltaksgruppe.UTFAS, false),
        SYSSOFF("Sysselsettingstiltak i offentlig sektor for yrkeshemmede", Tiltaksgruppe.UTFAS, false),
        LONNTIL("Tidsbegrenset lønnstilskudd", Tiltaksgruppe.UTFAS, false),
        TIDSUBLONN("Tidsubestemt lønnstilskudd", Tiltaksgruppe.UTFAS, false),
        AMBF3("Tilrettelagt arbeid i arbeidsmarkedsbedrift", Tiltaksgruppe.UTFAS, false),
        TILRETTEL("Tilrettelegging for arbeidstaker", Tiltaksgruppe.UTFAS, false),
        TILPERBED("Tilretteleggingstilskudd for arbeidssøker", Tiltaksgruppe.UTFAS, true),
        TILSJOBB("Tilskudd til sommerjobb", Tiltaksgruppe.LONNTILS, false),
        UFØREPENLØ("Uførepensjon som lønnstilskudd", Tiltaksgruppe.UTFAS, false),
        UTDYRK("Utdanning", Tiltaksgruppe.UTFAS, true),
        UTDPERMVIK("Utdanningspermisjoner", Tiltaksgruppe.UTFAS, false),
        VIKARBLED("Utdanningsvikariater", Tiltaksgruppe.UTFAS, false),
        UTBHLETTPS("Utredning/behandling lettere psykiske lidelser", Tiltaksgruppe.UTFAS, true),
        UTBHPSLD("Utredning/behandling lettere psykiske og sammensatte lidelser", Tiltaksgruppe.UTFAS, true),
        UTBHSAMLI("Utredning/behandling sammensatte lidelser", Tiltaksgruppe.UTFAS, true),
        UTVAOONAV("Utvidet oppfølging i NAV", Tiltaksgruppe.FORSOK, true),
        UTVOPPFOPL("Utvidet oppfølging i opplæring", Tiltaksgruppe.OPPFOLG, true),
        VARLONTIL("Varig lønnstilskudd", Tiltaksgruppe.LONNTILS, false),
        VATIAROR("Varig tilrettelagt arbeid i ordinær virksomhet", Tiltaksgruppe.VARIGASV, false),
        VASV("Varig tilrettelagt arbeid i skjermet virksomhet", Tiltaksgruppe.VARIGASV, false),
        VV("Varig vernet arbeid (VVA)", Tiltaksgruppe.UTFAS, false),
        VIDRSKOLE("Videregående skole", Tiltaksgruppe.UTFAS, false),
        OPPLT2AAR("2-årig opplæringstiltak", Tiltaksgruppe.UTFAS, true),
        FORSOPPLEV("Forsøk opplæringstiltak av lengre varighet", Tiltaksgruppe.FORSOK, true),
    }

    enum class DeltakerStatusType(val navn: String, val girRettTilÅASøke: Boolean) {
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