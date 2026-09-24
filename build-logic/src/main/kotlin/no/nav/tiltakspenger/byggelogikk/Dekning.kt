package no.nav.tiltakspenger.byggelogikk

import org.gradle.api.provider.Property

/**
 * Hvor langt modulen har kommet på grendekning.
 *
 * Full linjedekning sier ingenting om hvilken vei et vilkår ble tatt, og full grendekning sier ingenting om en linje uten grener.
 * De to målene utfyller hverandre, så grendekning legges til side om side med linjedekningen — den erstatter den ikke.
 *
 * Trinnene finnes fordi et repo sjelden er i mål samme dag som kravet innføres.
 * [RAPPORTER] gjør avviket synlig i bygget uten å stoppe det, slik at gapet kan lukkes før gaten smekker igjen.
 */
enum class Grendekning {
    /**
     * Ingen grendekningsregel.
     * Utgangspunktet for en modul som ikke har begynt.
     */
    AV,

    /** Regelen kjøres, men et brudd logges som advarsel i stedet for å feile bygget. */
    RAPPORTER,

    /** Regelen er en gate: bygget feiler når grendekningen faller under terskelen. */
    KREVES,
}

/** Konfigurasjon av dekningsgaten i en modul. */
abstract class Dekning {

    /**
     * Trinnet modulen står på for grendekning.
     * [Grendekning.AV] som standard, slik at eksisterende moduler beholder linjegaten sin uendret.
     */
    abstract val grener: Property<Grendekning>

    /**
     * Terskelen grendekningen måles mot, i prosent.
     * 100 som standard.
     * En lavere verdi er en skralle på vei mot 100: den låser det modulen allerede har oppnådd, slik at dekningen ikke kan falle tilbake mens gapet lukkes.
     */
    abstract val grenterskel: Property<Int>
}
