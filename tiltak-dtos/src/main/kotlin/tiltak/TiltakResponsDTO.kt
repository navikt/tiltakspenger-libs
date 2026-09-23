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
        val arenaKode: TiltakTypeDTO,
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

    enum class TiltakTypeDTO(
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

        // Lagt til 2025-03-02 - Team Tiltak - https://lovdata.no/nav/forskrift/2026-02-04-163
        FIREARIG_LONNSTILSKUDD("Forskrift om forsøk med fireårig lønnstilskudd for unge", false),

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

    enum class DeltakerStatusTypeDTO(
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

fun TiltakResponsDTO.TiltakTypeDTO.toTiltakstypeSomGirRett(): Either<TiltakstypeGirIkkeRett, TiltakstypeSomGirRettDTO> =
    when (this) {
        TiltakResponsDTO.TiltakTypeDTO.ARBEIDSMARKEDSOPPLAERING -> TiltakstypeSomGirRettDTO.ARBEIDSMARKEDSOPPLAERING.right()

        TiltakResponsDTO.TiltakTypeDTO.ARBFORB -> TiltakstypeSomGirRettDTO.ARBEIDSFORBEREDENDE_TRENING.right()

        TiltakResponsDTO.TiltakTypeDTO.ARBRRHDAG -> TiltakstypeSomGirRettDTO.ARBEIDSRETTET_REHABILITERING.right()

        TiltakResponsDTO.TiltakTypeDTO.ARBTREN -> TiltakstypeSomGirRettDTO.ARBEIDSTRENING.right()

        TiltakResponsDTO.TiltakTypeDTO.AVKLARAG -> TiltakstypeSomGirRettDTO.AVKLARING.right()

        TiltakResponsDTO.TiltakTypeDTO.DIGIOPPARB -> TiltakstypeSomGirRettDTO.DIGITAL_JOBBKLUBB.right()

        TiltakResponsDTO.TiltakTypeDTO.ENKELAMO -> TiltakstypeSomGirRettDTO.ENKELTPLASS_AMO.right()

        TiltakResponsDTO.TiltakTypeDTO.ENKFAGYRKE -> TiltakstypeSomGirRettDTO.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG.right()

        TiltakResponsDTO.TiltakTypeDTO.FAG_OG_YRKESOPPLAERING -> TiltakstypeSomGirRettDTO.FAG_OG_YRKESOPPLAERING.right()

        TiltakResponsDTO.TiltakTypeDTO.FORSOPPLEV -> TiltakstypeSomGirRettDTO.FORSØK_OPPLÆRING_LENGRE_VARIGHET.right()

        TiltakResponsDTO.TiltakTypeDTO.GRUPPEAMO -> TiltakstypeSomGirRettDTO.GRUPPE_AMO.right()

        TiltakResponsDTO.TiltakTypeDTO.GRUFAGYRKE -> TiltakstypeSomGirRettDTO.GRUPPE_VGS_OG_HØYERE_YRKESFAG.right()

        TiltakResponsDTO.TiltakTypeDTO.HOYEREUTD -> TiltakstypeSomGirRettDTO.HØYERE_UTDANNING.right()

        TiltakResponsDTO.TiltakTypeDTO.HOYERE_YRKESFAGLIG_UTDANNING -> TiltakstypeSomGirRettDTO.HOYERE_YRKESFAGLIG_UTDANNING.right()

        TiltakResponsDTO.TiltakTypeDTO.INDJOBSTOT -> TiltakstypeSomGirRettDTO.INDIVIDUELL_JOBBSTØTTE.right()

        TiltakResponsDTO.TiltakTypeDTO.IPSUNG -> TiltakstypeSomGirRettDTO.INDIVIDUELL_KARRIERESTØTTE_UNG.right()

        TiltakResponsDTO.TiltakTypeDTO.JOBBK -> TiltakstypeSomGirRettDTO.JOBBKLUBB.right()

        TiltakResponsDTO.TiltakTypeDTO.INDOPPFAG -> TiltakstypeSomGirRettDTO.OPPFØLGING.right()

        TiltakResponsDTO.TiltakTypeDTO.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> TiltakstypeSomGirRettDTO.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV.right()

        TiltakResponsDTO.TiltakTypeDTO.STUDIESPESIALISERING -> TiltakstypeSomGirRettDTO.STUDIESPESIALISERING.right()

        TiltakResponsDTO.TiltakTypeDTO.UTVAOONAV -> TiltakstypeSomGirRettDTO.UTVIDET_OPPFØLGING_I_NAV.right()

        TiltakResponsDTO.TiltakTypeDTO.UTVOPPFOPL -> TiltakstypeSomGirRettDTO.UTVIDET_OPPFØLGING_I_OPPLÆRING.right()

        TiltakResponsDTO.TiltakTypeDTO.REFINO,
        TiltakResponsDTO.TiltakTypeDTO.MIDLONTIL,
        TiltakResponsDTO.TiltakTypeDTO.VARLONTIL,
        TiltakResponsDTO.TiltakTypeDTO.TILSJOBB,
        TiltakResponsDTO.TiltakTypeDTO.ETAB,
        TiltakResponsDTO.TiltakTypeDTO.VASV,
        TiltakResponsDTO.TiltakTypeDTO.VATIAROR,
        TiltakResponsDTO.TiltakTypeDTO.FUNKSJASS,
        TiltakResponsDTO.TiltakTypeDTO.INKLUTILS,
        TiltakResponsDTO.TiltakTypeDTO.EKSPEBIST,
        TiltakResponsDTO.TiltakTypeDTO.MENTOR,
        TiltakResponsDTO.TiltakTypeDTO.FIREARIG_LONNSTILSKUDD,
        TiltakResponsDTO.TiltakTypeDTO.AMBF1,
        TiltakResponsDTO.TiltakTypeDTO.KURS,
        TiltakResponsDTO.TiltakTypeDTO.ANNUTDANN,
        TiltakResponsDTO.TiltakTypeDTO.ABOPPF,
        TiltakResponsDTO.TiltakTypeDTO.ABUOPPF,
        TiltakResponsDTO.TiltakTypeDTO.ABIST,
        TiltakResponsDTO.TiltakTypeDTO.ABTBOPPF,
        TiltakResponsDTO.TiltakTypeDTO.AMO,
        TiltakResponsDTO.TiltakTypeDTO.AMOE,
        TiltakResponsDTO.TiltakTypeDTO.AMOB,
        TiltakResponsDTO.TiltakTypeDTO.AMOY,
        TiltakResponsDTO.TiltakTypeDTO.PRAKSORD,
        TiltakResponsDTO.TiltakTypeDTO.PRAKSKJERM,
        TiltakResponsDTO.TiltakTypeDTO.ARBRRHBAG,
        TiltakResponsDTO.TiltakTypeDTO.ARBRRHBSM,
        TiltakResponsDTO.TiltakTypeDTO.ARBRDAGSM,
        TiltakResponsDTO.TiltakTypeDTO.ARBRRDOGN,
        TiltakResponsDTO.TiltakTypeDTO.ARBDOGNSM,
        TiltakResponsDTO.TiltakTypeDTO.ASV,
        TiltakResponsDTO.TiltakTypeDTO.ATG,
        TiltakResponsDTO.TiltakTypeDTO.AVKLARUS,
        TiltakResponsDTO.TiltakTypeDTO.AVKLARSP,
        TiltakResponsDTO.TiltakTypeDTO.AVKLARKV,
        TiltakResponsDTO.TiltakTypeDTO.AVKLARSV,
        TiltakResponsDTO.TiltakTypeDTO.BIA,
        TiltakResponsDTO.TiltakTypeDTO.BREVKURS,
        TiltakResponsDTO.TiltakTypeDTO.DIVTILT,
        TiltakResponsDTO.TiltakTypeDTO.FLEKSJOBB,
        TiltakResponsDTO.TiltakTypeDTO.TILRTILSK,
        TiltakResponsDTO.TiltakTypeDTO.KAT,
        TiltakResponsDTO.TiltakTypeDTO.VALS,
        TiltakResponsDTO.TiltakTypeDTO.GRUNNSKOLE,
        TiltakResponsDTO.TiltakTypeDTO.HOYSKOLE,
        TiltakResponsDTO.TiltakTypeDTO.INDOPPFOLG,
        TiltakResponsDTO.TiltakTypeDTO.ITGRTILS,
        TiltakResponsDTO.TiltakTypeDTO.JOBBKLUBB,
        TiltakResponsDTO.TiltakTypeDTO.JOBBFOKUS,
        TiltakResponsDTO.TiltakTypeDTO.JOBBBONUS,
        TiltakResponsDTO.TiltakTypeDTO.JOBBSKAP,
        TiltakResponsDTO.TiltakTypeDTO.AMBF2,
        TiltakResponsDTO.TiltakTypeDTO.STATLAERL,
        TiltakResponsDTO.TiltakTypeDTO.LONNTILS,
        TiltakResponsDTO.TiltakTypeDTO.REAKTUFOR,
        TiltakResponsDTO.TiltakTypeDTO.LONNTILL,
        TiltakResponsDTO.TiltakTypeDTO.NETTAMO,
        TiltakResponsDTO.TiltakTypeDTO.NETTKURS,
        TiltakResponsDTO.TiltakTypeDTO.INST_S,
        TiltakResponsDTO.TiltakTypeDTO.INDOPPFSP,
        TiltakResponsDTO.TiltakTypeDTO.SYSSLANG,
        TiltakResponsDTO.TiltakTypeDTO.YHEMMOFF,
        TiltakResponsDTO.TiltakTypeDTO.SYSSOFF,
        TiltakResponsDTO.TiltakTypeDTO.LONNTIL,
        TiltakResponsDTO.TiltakTypeDTO.TIDSUBLONN,
        TiltakResponsDTO.TiltakTypeDTO.AMBF3,
        TiltakResponsDTO.TiltakTypeDTO.TILRETTEL,
        TiltakResponsDTO.TiltakTypeDTO.TILPERBED,
        TiltakResponsDTO.TiltakTypeDTO.PV,
        TiltakResponsDTO.TiltakTypeDTO.SPA,
        TiltakResponsDTO.TiltakTypeDTO.UFØREPENLØ,
        TiltakResponsDTO.TiltakTypeDTO.UTDYRK,
        TiltakResponsDTO.TiltakTypeDTO.UTDPERMVIK,
        TiltakResponsDTO.TiltakTypeDTO.VIKARBLED,
        TiltakResponsDTO.TiltakTypeDTO.UTBHLETTPS,
        TiltakResponsDTO.TiltakTypeDTO.UTBHPSLD,
        TiltakResponsDTO.TiltakTypeDTO.UTBHSAMLI,
        TiltakResponsDTO.TiltakTypeDTO.VV,
        TiltakResponsDTO.TiltakTypeDTO.VIDRSKOLE,
        TiltakResponsDTO.TiltakTypeDTO.OPPLT2AAR,
        TiltakResponsDTO.TiltakTypeDTO.LONNTILAAP,
        TiltakResponsDTO.TiltakTypeDTO.BIO,
        TiltakResponsDTO.TiltakTypeDTO.TESTING,
        TiltakResponsDTO.TiltakTypeDTO.NYTEST,
        TiltakResponsDTO.TiltakTypeDTO.INDOPPRF,
        TiltakResponsDTO.TiltakTypeDTO.SUPPEMP,
        // Disse har gitt rett tidligere, men er ikke aktuelle mer.
        // Behandler dem som om de ikke gir rett fordi det ikke finnes utbetalingskoder for dem.
        TiltakResponsDTO.TiltakTypeDTO.FORSAMOGRU,
        TiltakResponsDTO.TiltakTypeDTO.FORSAMOENK,
        TiltakResponsDTO.TiltakTypeDTO.FORSFAGGRU,
        TiltakResponsDTO.TiltakTypeDTO.FORSFAGENK,
        TiltakResponsDTO.TiltakTypeDTO.FORSHOYUTD,
        -> TiltakstypeGirIkkeRett.left()
    }
