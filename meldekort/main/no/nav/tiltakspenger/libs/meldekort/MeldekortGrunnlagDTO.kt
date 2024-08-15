package no.nav.tiltakspenger.libs.meldekort

import java.time.LocalDate

/**
 * Kontrakt mellom tiltakspenger-vedtak og tiltakspenger-meldekort-api
 */
data class MeldekortGrunnlagDTO(
    val vedtakId: String,
    val sakId: String,
    val behandlingId: String,
    val status: StatusDTO,
    val vurderingsperiode: PeriodeDTO,
    val tiltak: List<TiltakDTO>,
    val personopplysninger: PersonopplysningerDTO,
    val utfallsperioder: List<UtfallsperiodeDTO>,
)

data class UtfallsperiodeDTO(
    val fom: String,
    val tom: String,
    val utfall: UtfallForPeriodeDTO,
)

enum class UtfallForPeriodeDTO {
    GIR_RETT_TILTAKSPENGER,
    GIR_IKKE_RETT_TILTAKSPENGER,
}

data class PersonopplysningerDTO(
    val fornavn: String,
    val etternavn: String,
    val ident: String,
)

enum class StatusDTO {
    AKTIV,
    IKKE_AKTIV,
}

data class TiltakDTO(
    val periodeDTO: PeriodeDTO,
    // TODO jah: Vurder å bruke enumen her.
    val typeKode: String,
    val antDagerIUken: Int,
)

data class PeriodeDTO(
    val fra: LocalDate,
    val til: LocalDate,
)
