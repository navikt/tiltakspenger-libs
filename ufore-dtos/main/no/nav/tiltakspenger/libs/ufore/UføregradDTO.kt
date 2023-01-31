package no.nav.tiltakspenger.libs.ufore

import java.time.LocalDate

data class UføregradDTO(
    val harUforegrad: Boolean,
    val datoUfor: LocalDate?,
    val virkDato: LocalDate?,
)
