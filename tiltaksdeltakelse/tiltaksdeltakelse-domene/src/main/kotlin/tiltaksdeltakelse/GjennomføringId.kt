package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Kildesystemets id for gjennomføringen deltakelsen hører til.
 *
 * Gjennomføringen er det konkrete tiltaket hos en arrangør, for eksempel «Oppfølging hos Arrangør AS i Strandveien».
 * En person knyttes til en gjennomføring, og det er den koblingen som er deltakelsen.
 *
 * Kun Komet oppgir denne.
 * For Arena og Team Tiltak er den fraværende, og skal da være `null` — ikke tom streng, slik den gamle modellen brukte.
 */
@JvmInline
value class GjennomføringId(
    val verdi: String,
) {
    init {
        require(verdi.isNotBlank()) { "GjennomføringId kan ikke være tom" }
    }

    override fun toString(): String = verdi
}
