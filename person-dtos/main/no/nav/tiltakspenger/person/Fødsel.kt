package no.nav.tiltakspenger.person

import java.time.LocalDate

data class Fødsel(
    val foedselsdato: LocalDate,
    override val folkeregistermetadata: FolkeregisterMetadata,
    override val metadata: EndringsMetadata
) : Changeable

const val FREG = "FREG"
