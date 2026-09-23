package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

/**
 * Fødselsnummer slik kontrakten bærer det — en ren streng på wiren (`@JvmInline` pakkes ut av Jackson).
 * Maskeres i `toString` siden identen er et fødselsnummer; verdien hentes eksplisitt fra [verdi].
 */
@JvmInline
value class NorskIdentDto(val verdi: String) {
    override fun toString() = "***********"
}
