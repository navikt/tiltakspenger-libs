package no.nav.tiltakspenger.libs.periode

/**
 * Om to perioder overlapper, når minst én av dem kan mangle datoer.
 *
 * Trippelverdien er ekte: mangler en dato, kan vi ofte hverken bekrefte eller avkrefte overlapp.
 * En `Boolean?` ville skjult hva `null` betyr — [Kanskje] sier det rett ut.
 */
enum class Overlapp {
    Ja,
    Nei,
    Kanskje,
}
