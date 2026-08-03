package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Kopi av kontrakten `TiltakshistorikkV1Dto` fra `tiltakshistorikk` (Team Valp).
 *
 * Kontrakt: https://github.com/navikt/mulighetsrommet/blob/main/common/tiltakshistorikk-client/src/main/kotlin/no/nav/tiltak/historikk/TiltakshistorikkV1Dto.kt
 *
 * Kopien speiler kontrakten felt for felt, og hvert bevisst utelatt felt står som kommentar med begrunnelse — et avvik fra kontrakten skal være et synlig valg, ikke et stille tap.
 * Status, tiltakskoder, Komet-årsak og meldinger deserialiseres som `String`: tillegg hos kilden er forventet driftsmodus og skal aldri velte deserialiseringen.
 * Klassifiseringen til domenets kjente/ukjente verdier skjer i mapperen, ikke her.
 *
 * Kontraktens `opphav`-felt finnes ikke på wiren — det settes i klassekroppen hos kilden og er ikke med i kotlinx-serialiseringen deres.
 * Kildesystemet identifiseres av `type`-diskriminatoren.
 *
 * Ikke logg denne typen: radene bærer fødselsnummer, og tittel/arrangørnavn er stedsinformasjon.
 * Sikkerlogg bruker `metadata.rawResponseString`, som allerede bærer hele responsen.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true,
    defaultImpl = TiltakshistorikkV1Dto.UkjentDeltakelse::class,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = TiltakshistorikkV1Dto.ArenaDeltakelse::class, name = "ArenaDeltakelse"),
    JsonSubTypes.Type(value = TiltakshistorikkV1Dto.TeamKometDeltakelse::class, name = "TeamKometDeltakelse"),
    JsonSubTypes.Type(value = TiltakshistorikkV1Dto.TeamTiltakAvtale::class, name = "TeamTiltakAvtale"),
)
sealed interface TiltakshistorikkV1Dto {

    data class Virksomhet(
        // organisasjonsnummer er bevisst utelatt: tas det inn må det gjennom Personopplysning-vurderingen først, siden enkeltpersonforetak gjør orgnr personhenførbart.
        /**
         * Navn på virksomhet vil stort sett være tilgjengelig, men kan mangle for eldre tiltaksdeltakelser.
         */
        val navn: String?,
    )

    data class Arrangor(
        /**
         * Hovedenhet/juridisk enhet hos arrangør (fra brreg).
         */
        val hovedenhet: Virksomhet?,
        /**
         * Underenhet hos arrangør (fra brreg) som tiltaksgjennomføringen er registrert på.
         * Kontrakten garanterer at den finnes, og at den kan være en «utenlandsk arrangør» fra Arena.
         */
        val underenhet: Virksomhet,
    )

    data class Gjennomforing(
        val id: UUID,
        // navn er bevisst utelatt: kontrakten advarer selv om at feltet er fritekst og kan inneholde persondata for eldre Arena-deltakelser, og anbefaler tittel i stedet.
        /**
         * Deltidsprosent definert på gjennomføringen; gjelder deltakelsene på tiltaket når de ikke har egen.
         */
        val deltidsprosent: Float?,
    )

    /**
     * Tiltakstypen slik kilden oppga den.
     * Kontrakten har én nestet `Tiltakstype` per variant (Arena: fri streng, Komet og Team Tiltak: enum), men med `String`-deserialisering er formen identisk, så kopien deler én type.
     */
    data class Tiltakstype(
        val tiltakskode: String,
        val navn: String,
    )

    data class ArenaDeltakelse(
        val norskIdent: NorskIdentDto,
        val startDato: LocalDate?,
        val sluttDato: LocalDate?,
        /**
         * Kontraktens id er tiltakshistorikk-intern for Arena-deltakelser (kontraktens egen KDoc sier det).
         * Nøkkelen vår er `TA<arenaId>`, som ligger i konsumentenes databaser og sendes fra søknadsfronten — mapperen bruker [arenaId].
         */
        val id: UUID,
        val tittel: String,
        val arenaId: Int,
        val status: String,
        val tiltakstype: Tiltakstype,
        val gjennomforing: Gjennomforing,
        val arrangor: Arrangor,
        val deltidsprosent: Float?,
        val dagerPerUke: Float?,
    ) : TiltakshistorikkV1Dto

    data class TeamKometDeltakelse(
        val norskIdent: NorskIdentDto,
        val startDato: LocalDate?,
        val sluttDato: LocalDate?,
        val id: UUID,
        val tittel: String,
        val status: Status,
        val tiltakstype: Tiltakstype,
        val gjennomforing: Gjennomforing,
        val arrangor: Arrangor,
        val deltidsprosent: Float?,
        val dagerPerUke: Float?,
    ) : TiltakshistorikkV1Dto {

        data class Status(
            val type: String,
            val aarsak: String?,
            /**
             * Kontrakten heter feltet `opprettetDato` i dag og har varslet omdøping til `opprettetTidspunkt` — aliaset leser begge.
             */
            @JsonAlias("opprettetTidspunkt")
            val opprettetDato: LocalDateTime,
        )
    }

    data class TeamTiltakAvtale(
        val norskIdent: NorskIdentDto,
        val startDato: LocalDate?,
        val sluttDato: LocalDate?,
        val id: UUID,
        val tittel: String,
        val tiltakstype: Tiltakstype,
        val status: String,
        val stillingsprosent: Float?,
        val dagerPerUke: Float?,
        val arbeidsgiver: Virksomhet,
    ) : TiltakshistorikkV1Dto

    /**
     * En deltakelsesform vi ikke kjenner igjen — kontrakten har fått en ny variant, eller `type`-feltet mangler.
     * Uten denne ville hele oppslaget veltet den dagen kilden legger til en fjerde deltakelsesform; samme driftsmodus-argument som for `String`-deserialiseringen av statusene.
     * Bærer kun diskriminatoren, siden vi ikke kan anta noe om resten av formen.
     * Blir aldri en tiltaksdeltakelse — den telles og varsles via hente-resultatet.
     */
    data class UkjentDeltakelse(
        val type: String?,
    ) : TiltakshistorikkV1Dto
}
