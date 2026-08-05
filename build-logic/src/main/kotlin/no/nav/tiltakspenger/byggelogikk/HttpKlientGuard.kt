package no.nav.tiltakspenger.byggelogikk

import org.gradle.api.provider.MapProperty

/**
 * Konfigurasjon av gaten som holder andre HTTP-klienter enn libs sin `httpklient` av runtime-classpathen.
 *
 * Konsist-regelen `IngenAndreHttpKlienter` dekker det vi selv skriver og deklarerer.
 * Gaten dekker det siste hullet: en klient som kommer inn transitivt gjennom en annen avhengighet, uten å stå i noen import eller byggfil.
 *
 * Ktor-klienten står bevisst ikke på [forbudteKlienter], og skal ikke legges til.
 * `ktor-server-auth` eksponerer `ktor-client-core` som `api` fordi OAuth-provideren bruker den, så den ligger på classpathen så lenge vi bruker ktor sin server-auth.
 * Ktor-klienten håndheves derfor i kildekoden og i byggfilene, ikke her.
 */
abstract class HttpKlientGuard {

    /**
     * Koordinatprefikser modulen bevisst slipper gjennom, med begrunnelsen for hvert unntak.
     * Settes gjennom [tillat].
     */
    abstract val tillatte: MapProperty<String, String>

    /**
     * Slipper klienten bak [koordinatprefiks] gjennom gaten i denne modulen.
     * [begrunnelse] er påkrevd, fordi et unntak uten grunn er et unntak ingen tør fjerne igjen.
     */
    fun tillat(koordinatprefiks: String, begrunnelse: String) {
        require(begrunnelse.isNotBlank()) {
            "Unntaket for $koordinatprefiks mangler begrunnelse."
        }
        tillatte.put(koordinatprefiks, begrunnelse)
    }

    companion object {
        /** Klientbibliotekene som ikke skal ligge på runtime-classpathen uten et uttalt unntak. */
        val forbudteKlienter = listOf(
            "com.squareup.okhttp3",
            "com.squareup.retrofit2",
            "org.apache.httpcomponents",
            "com.github.kittinunf.fuel",
            "com.konghq:unirest",
            "io.vertx:vertx-web-client",
            "org.http4k:http4k-client",
            "io.github.openfeign",
        )
    }
}
