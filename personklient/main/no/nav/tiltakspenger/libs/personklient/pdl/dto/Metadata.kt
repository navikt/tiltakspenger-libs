package no.nav.tiltakspenger.libs.personklient.pdl.dto

import java.time.LocalDateTime

internal data class Endring(
    val kilde: String,
    val registrert: LocalDateTime?,
    val registrertAv: String,
    val systemkilde: String,
    val type: String,
)

internal fun List<Endring>.nyeste(): Endring? = this
    .filter { it.registrert != null }
    .maxByOrNull { it.registrert!! }

internal data class EndringsMetadata(
    val endringer: List<Endring>,
    val master: String,
)
