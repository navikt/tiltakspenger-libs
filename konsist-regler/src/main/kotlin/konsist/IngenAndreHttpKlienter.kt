package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * HTTP-kall mot andre tjenester går via `httpklient`-modulen i libs, aldri direkte via en annen HTTP-klient.
 * Regelen sperrer importer fra kjente klient-API-er: Ktor-klienten (`io.ktor.client`), JDK-klienten (`java.net.http` og `HttpURLConnection`), OkHttp, Apache HttpClient og Fuel.
 * HTTP-vokabular utenfor klientpakkene er bevisst tillatt — f.eks. `io.ktor.http.ContentType`, `io.ktor.http.HttpHeaders` og `java.net.URI`.
 * Legitime unntak er implementasjonen selv (`httpklient`-infrastrukturen bygger transporten på JDK-klienten) og testhjelpere mot ktor sin `testApplication`, der test-klienten er eneste vei inn til test-serveren.
 * Kalleren velger scope (typisk `scopeFromProduction()`) og unntar slike filer via scope-slicing eller [unntatteFilstier] (sti-suffikser), f.eks. for klienter som ennå ikke er migrert.
 */
object IngenAndreHttpKlienter {

    private val forbudtePrefikser = listOf(
        "io.ktor.client.",
        "java.net.http.",
        "java.net.HttpURLConnection",
        "okhttp3.",
        "com.squareup.okhttp",
        "org.apache.hc.",
        "org.apache.http.",
        "com.github.kittinunf.fuel",
    )

    fun brudd(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope.kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            file.imports
                .filter { import -> forbudtePrefikser.any { prefiks -> import.name.startsWith(prefiks) } }
                .map { import -> "${file.path}: ${import.name}" }
        }

    fun assert(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, unntatteFilstier),
        "HTTP-kall går via libs sin httpklient. Følgende importer av andre HTTP-klienter er ikke tillatt.",
    )
}
