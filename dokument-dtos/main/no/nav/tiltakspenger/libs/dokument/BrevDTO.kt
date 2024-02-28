package no.nav.tiltakspenger.libs.dokument

import java.time.LocalDate

class BrevDTO(
    val personaliaDTO: PersonaliaDTO,
    val tiltaksinfoDTO: TiltaksinfoDTO,
    val fraDato: String,
    val tilDato: String,
    val saksnummer: String,
    val barnetillegg: Boolean,
    val saksbehandler: String,
    val kontor: String,
    val datoForUtsending: LocalDate,
)

data class PersonaliaDTO(
    val ident: String,
    val fornavn: String,
    val etternavn: String,
    val antallBarn: Int,
)

data class TiltaksinfoDTO(
    val tiltak: String,
    val tiltaksnavn: String,
    val tiltaksnummer: String,
    val arrangør: String,
)
