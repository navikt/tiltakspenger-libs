package no.nav.tiltakspenger.libs.personklient.pdl.dto

import java.time.LocalDateTime

data class Endring(
    val kilde: String,
    val registrert: LocalDateTime?,
    val registrertAv: String,
    val systemkilde: String,
    val type: String,
)

fun List<Endring>.nyeste(): Endring? = this
    .filter { it.registrert != null }
    .maxByOrNull { it.registrert!! }

data class EndringsMetadata(
    val endringer: List<Endring> = emptyList(),
    val master: String,
)
