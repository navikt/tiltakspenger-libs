package no.nav.tiltakspenger.libs.common

/**
 * Dette er en rolle en person som bruker tiltakspenger-systemet kan ha.
 */
enum class Saksbehandlerrolle : Rolle {
    SAKSBEHANDLER,
    BESLUTTER,
    VEILEDER,
    UTVIKLER,
    TILBAKEKREVING,
}
