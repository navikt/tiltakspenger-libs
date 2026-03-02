package no.nav.tiltakspenger.libs.tiltak

import arrow.core.Either
import arrow.core.left
import arrow.core.right
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

    enum class TiltakType(
        val navn: String,
        val rettPåTiltakspenger: Boolean,
    ) {
        // gir rett
        ARBTREN("Arbeidstrening", true),
        GRUPPEAMO("Gruppe AMO", true),
        ENKELAMO("Enkeltplass AMO", true),
        GRUFAGYRKE("Gruppe Fag- og yrkesopplæring VGS og høyere yrkesfaglig utdanning", true),
        ENKFAGYRKE("Enkeltplass Fag- og yrkesopplæring VGS og høyere yrkesfaglig utdanning", true),
        HOYEREUTD("Høyere utdanning", true),
        ARBFORB("Arbeidsforberedende trening (AFT)", true),
        UTVAOONAV("Arbeid med støtte", true),
        FORSAMOGRU("Forsøk AMO gruppe", true),
        FORSAMOENK("Forsøk AMO enkeltplass", true),
        FORSFAGGRU("Forsøk fag- og yrkesopplæring gruppe", true),
        FORSFAGENK("Forsøk fag- og yrkesopplæring enkeltplass", true),
        FORSHOYUTD("Forsøk høyere utdanning", true),
        FORSOPPLEV("Forsøk opplæringstiltak av lengre varighet", true),
        AVKLARAG("Avklaring", true),
        ARBRRHDAG("Arbeidsrettet rehabilitering (dag)", true),
        JOBBK("Jobbklubb", true),
        INDOPPFAG("Oppfølging", true),
        INDJOBSTOT("Individuell jobbstøtte (IPS)", true),
        DIGIOPPARB("Digitalt jobbsøkerkurs", true),
        UTVOPPFOPL("Utvidet oppfølging i opplæring", true),
        IPSUNG("Individuell karrierestøtte (IPS Ung)", true),

        // nye tiltakskoder som ikke finnes i Arena, gir rett
        ARBEIDSMARKEDSOPPLAERING("Arbeidsmarkedsopplæring (AMO)", true),
        NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV("Norskopplæring, grunnleggende ferdigheter og FOV", true),
        STUDIESPESIALISERING("Studiespesialisering", true),
        FAG_OG_YRKESOPPLAERING("Fag- og yrkesopplæring", true),
        HOYERE_YRKESFAGLIG_UTDANNING("Høyere yrkesfaglig utdanning", true),

        // gir ikke rett
        REFINO("Resultatbasert finansiering av oppfølging", false),
        MIDLONTIL("Midlertidig lønnstilskudd", false),
        VARLONTIL("Varig lønnstilskudd", false),
        TILSJOBB("Tilskudd til sommerjobb", false),
        ETAB("Egenetablering", false),
        VASV("Varig tilrettelagt arbeid i skjermet virksomhet", false),
        VATIAROR("Varig tilrettelagt arbeid i ordinær virksomhet", false),
        FUNKSJASS("Funksjonsassistanse", false),
        INKLUTILS("Inkluderingstilskudd", false),
        EKSPEBIST("Ekspertbistand", false),
        MENTOR("Mentor", false),

        // utfases
        AMBF1("AMB Avklaring (fase 1)", false),
        KURS("Andre kurs", false),
        ANNUTDANN("Annen utdanning", false),
        ABOPPF("Arbeid med bistand A oppfølging", false),
        ABUOPPF("Arbeid med bistand A utvidet oppfølging", false),
        ABIST("Arbeid med Bistand (AB)", false),
        ABTBOPPF("Arbeid med bistand B", false),
        AMO("Arbeidsmarkedsopplæring (AMO)", false),
        AMOE("Arbeidsmarkedsopplæring (AMO) enkeltplass", false),
        AMOB("Arbeidsmarkedsopplæring (AMO) i bedrift", false),
        AMOY("Arbeidsmarkedsopplæring (AMO) yrkeshemmede", false),
        PRAKSORD("Arbeidspraksis i ordinær virksomhet", false),
        PRAKSKJERM("Arbeidspraksis i skjermet virksomhet", false),
        ARBRRHBAG("Arbeidsrettet rehabilitering", false),
        ARBRRHBSM("Arbeidsrettet rehabilitering - sykmeldt arbeidstaker", false),
        ARBRDAGSM("Arbeidsrettet rehabilitering (dag) - sykmeldt arbeidstaker", false),
        ARBRRDOGN("Arbeidsrettet rehabilitering (døgn)", false),
        ARBDOGNSM("Arbeidsrettet rehabilitering (døgn) - sykmeldt arbeidstaker", false),
        ASV("Arbeidssamvirke (ASV)", false),
        ATG("Arbeidstreningsgrupper", false),
        AVKLARUS("Avklaring", false),
        AVKLARSP("Avklaring - sykmeldt arbeidstaker", false),
        AVKLARKV("Avklaring av kortere varighet", false),
        AVKLARSV("Avklaring i skjermet virksomhet", false),
        BIA("Bedriftsintern attføring", false),
        BREVKURS("Brevkurs", false),
        DIVTILT("Diverse tiltak", false),
        FLEKSJOBB("Fleksibel jobb - lønnstilskudd av lengre varighet", false),
        TILRTILSK(
            "Forebyggings- og tilretteleggingstilskudd IA virksomheter og BHT-honorar",
            false,
        ),
        KAT("Formidlingstjenester", false),
        VALS("Formidlingstjenester - Ventelønn", false),
        GRUNNSKOLE("Grunnskole", false),
        HOYSKOLE("Høyskole/Universitet", false),
        INDOPPFOLG("Individuelt oppfølgingstiltak", false),
        ITGRTILS("Integreringstilskudd", false),
        JOBBKLUBB("Intern jobbklubb", false),
        JOBBFOKUS("Jobbfokus/Utvidet formidlingsbistand", false),
        JOBBBONUS("Jobbklubb med bonusordning", false),
        JOBBSKAP("Jobbskapingsprosjekter", false),
        AMBF2("Kvalifisering i arbeidsmarkedsbedrift", false),
        STATLAERL("Lærlinger i statlige etater", false),
        LONNTILS("Lønnstilskudd", false),
        REAKTUFOR("Lønnstilskudd - reaktivisering av uførepensjonister", false),
        LONNTILL("Lønnstilskudd av lengre varighet", false),
        NETTAMO("Nettbasert arbeidsmarkedsopplæring (AMO)", false),
        NETTKURS("Nettkurs", false),
        INST_S("Nye plasser institusjonelle tiltak", false),
        INDOPPFSP("Oppfølging - sykmeldt arbeidstaker", false),
        SYSSLANG("Sysselsettingstiltak for langtidsledige", false),
        YHEMMOFF("Sysselsettingstiltak for yrkeshemmede", false),
        SYSSOFF("Sysselsettingstiltak i offentlig sektor for yrkeshemmede", false),
        LONNTIL("Tidsbegrenset lønnstilskudd", false),
        TIDSUBLONN("Tidsubestemt lønnstilskudd", false),
        AMBF3("Tilrettelagt arbeid i arbeidsmarkedsbedrift", false),
        TILRETTEL("Tilrettelegging for arbeidstaker", false),
        TILPERBED("Tilretteleggingstilskudd for arbeidssøker", false),
        PV("Produksjonsverksted (PV)", false),
        SPA("Spa prosjekter", false),
        UFØREPENLØ("Uførepensjon som lønnstilskudd", false),
        UTDYRK("Utdanning", false),
        UTDPERMVIK("Utdanningspermisjoner", false),
        VIKARBLED("Utdanningsvikariater", false),
        UTBHLETTPS("Utredning/behandling lettere psykiske lidelser", false),
        UTBHPSLD("Utredning/behandling lettere psykiske og sammensatte lidelser", false),
        UTBHSAMLI("Utredning/behandling sammensatte lidelser", false),
        VV("Varig vernet arbeid (VVA)", false),
        VIDRSKOLE("Videregående skole", false),
        OPPLT2AAR("2-årig opplæringstiltak", false),

        // rariteter
        LONNTILAAP("Arbeidsavklaringspenger som lønnstilskudd", false),
        BIO("Bedriftsintern opplæring (BIO)", false),
        TESTING("Lenes testtiltak", false),
        NYTEST("Nytt testtiltak", false),
        INDOPPRF("Resultatbasert finansiering av formidlingsbistand", false),
        SUPPEMP("Supported Employment", false),
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
