package no.nav.tiltakspenger.libs.arena.ytelse

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Collections.emptyList

data class ArenaYtelseResponsDTO(
    val saker: List<SakDTO>? = null,
    val feil: FeilmeldingDTO? = null,
) {

    enum class FeilmeldingDTO(val melding: String) {
        UkjentFeil("Ukjent feil"),
    }

    data class SakDTO(
        val gyldighetsperiodeFom: LocalDateTime,
        val gyldighetsperiodeTom: LocalDateTime?,
        val kravMottattDato: LocalDate?,
        val fagsystemSakId: String? = null,
        val status: SakStatusType? = null,
        val sakType: SakType? = null,
        val vedtak: List<VedtakDTO> = emptyList(),
        val antallDagerIgjen: Int? = null,
        val antallUkerIgjen: Int? = null,
    )

    enum class SakStatusType(val navn: String) {
        AKTIV("Aktiv"),
        AVSLU("Lukket"),
        INAKT("Inaktiv"),
    }

    enum class SakType(val navn: String) {
        AA("Arbeidsavklaringspenger"),
        DAGP("Dagpenger"),
        INDIV("Individstønad"),
        ANNET("Alt annet"),
    }

    data class VedtakDTO(
        val beslutningsDato: LocalDate? = null,
        val vedtakType: VedtakType? = null,
        val vedtaksperiodeFom: LocalDate? = null,
        val vedtaksperiodeTom: LocalDate? = null,
        val rettighetType: RettighetType? = null,
        val status: VedtakStatusType? = null,
    )

    enum class VedtakType(val navn: String) {
        E("Endring"),
        F("Forlenget ventetid"), // Gjelder ikke tiltakspenger
        G("Gjenopptak"),
        N("Annuller sanksjon"), // Gjelder ikke tiltakspenger
        O("Ny rettighet"),
        S("Stans"),
        A("Reaksjon"), // Står ikke listet opp i dokumentasjonen..
        K("Kontroll"), // Står ikke listet opp i dokumentasjonen..
        M("Omgjør reaksjon"), // Står ikke listet opp i dokumentasjonen..
        R("Revurdering"), // Står ikke listet opp i dokumentasjonen..
        T("Tidsbegrenset bortfall"), // Gjelder ikke tiltakspenger
    }

    enum class RettighetType(val navn: String, val sakType: SakType) {
        AAP("Arbeidsavklaringspenger", SakType.AA),
        DAGO("Ordinære dagpenger", SakType.DAGP),
        PERM("Dagpenger under permitteringer", SakType.DAGP),
        FISK("Dagp. v/perm fra fiskeindustri", SakType.DAGP),
        LONN("Lønnsgarantimidler - dagpenger", SakType.DAGP),
        BASI("Tiltakspenger (basisytelse før 2014)", SakType.INDIV),
        AATFOR("Tvungen forvaltning", SakType.ANNET),
        AAUNGUFOR("Ung ufør", SakType.ANNET),
        AA115("§11-5 nedsatt arbeidsevne", SakType.ANNET),
        AA116("§11-6 behov for bistand", SakType.ANNET),
        ABOUT("Boutgifter", SakType.ANNET),
        ADAGR("Daglige reiseutgifter", SakType.ANNET),
        AFLYT("Flytting", SakType.ANNET),
        AHJMR("Hjemreise", SakType.ANNET),
        ANKE("Anke", SakType.ANNET),
        ARBT("Arbeidstreningplass", SakType.ANNET),
        ATIF("Tilsyn - familiemedlemmer", SakType.ANNET),
        ATIO("Tilsyn - barn over 10 år", SakType.ANNET),
        ATIU("Tilsyn - barn under 10 år", SakType.ANNET),
        ATTF("§11-6, nødvendig og hensiktsmessig tiltak", SakType.ANNET),
        ATTK("§11-5, sykdom, skade eller lyte", SakType.ANNET),
        ATTP("Attføringspenger", SakType.ANNET),
        AUNDM("Bøker og undervisningsmatriell", SakType.ANNET),
        BEHOV("Behovsvurdering", SakType.ANNET),
        BIST14A("Bistandsbehov §14a", SakType.ANNET),
        BORT("Borteboertillegg", SakType.ANNET),
        BOUT("Boutgifter", SakType.ANNET),
        BREI("MOB-Besøksreise", SakType.ANNET),
        BTIF("Barnetilsyn - familiemedlemmer", SakType.ANNET),
        BTIL("Barnetillegg", SakType.ANNET),
        BTIO("Barnetilsyn - barn over 10 år", SakType.ANNET),
        BTIU("Barnetilsyn - barn under 10 år", SakType.ANNET),
        DAGR("Daglige reiseutgifter", SakType.ANNET),
        DEKS("Eksport - dagpenger", SakType.ANNET),
        DIMP("Import (E303 inn)", SakType.ANNET),
        EKSG("Eksamensgebyr", SakType.ANNET),
        FADD("Fadder", SakType.ANNET),
        FETI("Ferietillegg", SakType.DAGP),
        FLYT("Flytting", SakType.ANNET),
        FREI("MOB-Fremreise", SakType.ANNET),
        FRI_MK_AAP("Fritak fra å sende meldekort AAP", SakType.ANNET),
        FRI_MK_IND("Fritak fra å sende meldekort individstønad", SakType.ANNET),
        FSTO("MOB-Flyttestønad", SakType.ANNET),
        HJMR("Hjemreise", SakType.ANNET),
        HREI("MOB-Hjemreise", SakType.ANNET),
        HUSH("Husholdsutgifter", SakType.ANNET),
        IDAG("Reisetillegg", SakType.ANNET),
        IEKS("Eksamensgebyr", SakType.ANNET),
        IFLY("MOB-Flyttehjelp", SakType.ANNET),
        INDIVFADD("Individstønad fadder", SakType.ANNET),
        IREI("Hjemreise", SakType.ANNET),
        ISEM("Semesteravgift", SakType.ANNET),
        ISKO("Skolepenger", SakType.ANNET),
        IUND("Bøker og undervisningsmatr.", SakType.ANNET),
        KLAG1("Klage underinstans", SakType.ANNET),
        KLAG2("Klage klageinstans", SakType.ANNET),
        KOMP("Kompensasjon for ekstrautgifter", SakType.ANNET),
        LREF("Refusjon av legeutgifter", SakType.ANNET),
        MELD("Meldeplikt attføring", SakType.ANNET),
        MITR("MOB-Midlertidig transporttilbud", SakType.ANNET),
        NVURD("Næringsfaglig vurdering", SakType.ANNET),
        REHAB("Rehabiliteringspenger", SakType.ANNET),
        RSTO("MOB-Reisestønad", SakType.ANNET),
        SANK_A("Sanksjon arbeidsgiver", SakType.ANNET),
        SANK_B("Sanksjon behandler", SakType.ANNET),
        SANK_S("Sanksjon sykmeldt", SakType.ANNET),
        SEMA("Semesteravgift", SakType.ANNET),
        SKOP("Skolepenger", SakType.ANNET),
        SREI("MOB-Sjømenn", SakType.ANNET),
        TFOR("Tvungen forvaltning", SakType.ANNET),
        TILBBET("Tilbakebetaling", SakType.ANNET),
        TILO("Tilsyn øvrige familiemedlemmer", SakType.ANNET),
        TILTAK("Tiltaksplass", SakType.ANNET),
        TILU("Tilsyn barn under 10 år", SakType.ANNET),
        UFOREYT("Uføreytelser", SakType.ANNET),
        UNDM("Bøker og undervisningsmatr.", SakType.ANNET),
        UTESTENG("Utestengning", SakType.ANNET),
        VENT("Ventestønad", SakType.ANNET),
    }

    enum class VedtakStatusType(val navn: String) {
        AVSLU("Avsluttet"),
        GODKJ("Godkjent"),
        INNST("Innstilt"),
        IVERK("Iverksatt"),
        MOTAT("Mottatt"),
        OPPRE("Opprettet"),
        REGIS("Registrert"),
    }
}
