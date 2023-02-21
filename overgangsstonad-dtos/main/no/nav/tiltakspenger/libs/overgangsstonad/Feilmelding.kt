package no.nav.tiltakspenger.libs.overgangsstonad

enum class Feilmelding(val message: String) {
    Feilet("Overgangsstønad feilet"),
    IkkeHentet("Overgangsstønad ikke hentet"),
    IkkeTilgang("Ikke tilgang til Overgangsstønad"),
    FunksjonellFeil("Funksjonell feil i Overgangsstønad"),
}
