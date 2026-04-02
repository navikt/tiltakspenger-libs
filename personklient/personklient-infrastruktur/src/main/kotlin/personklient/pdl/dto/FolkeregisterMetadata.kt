package no.nav.tiltakspenger.libs.personklient.pdl.dto

import java.time.LocalDateTime

data class FolkeregisterMetadata(
    val aarsak: String? = null,
    val ajourholdstidspunkt: LocalDateTime? = null,
    val gyldighetstidspunkt: LocalDateTime? = null,
    val kilde: String? = null,
    val opphoerstidspunkt: LocalDateTime? = null,
    val sekvens: Int? = null,
)
