package no.nav.tiltakspenger.libs.dokument

class BrevDTO(
    val personalia: PersonaliaDTO,
    val tiltaksinfo: TiltaksinfoDTO,
    val fraDato: String,
    val tilDato: String,
    val saksnummer: String,
    val barnetillegg: Boolean,
    val saksbehandler: String,
    val beslutter: String,
    val kontor: String,
    val datoForUtsending: String,
    val sats: Int,
    val satsBarn: Int,
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
