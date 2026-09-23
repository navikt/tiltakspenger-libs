package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Ikke oppgi `rewriteAudienceTarget` når du henter systemtokens fra Texas.
 * Parameteret er utgått: `TexasHttpClient` utleder selv om target må skrives om, av formen på scope-verdien.
 *
 * Bakgrunn: verdien flagget måtte stemme overens med står i en helt annen fil — nais-manifestet eller miljøkonfigurasjonen — og ingenting koblet de to.
 * Feil kombinasjon ga `invalid_scope` (AADSTS1002012) fra Entra ID på alle systemtokens, og tok ned `tiltakspenger-soknad-api` i produksjon to ganger.
 * Andre gang kom regresjonen av at wiringen ble flyttet til en ny fil og flagget ble med på flyttelasset.
 *
 * Regelen er tekstbasert og treffer både navngitt argument og deklarasjon, slik at også egne fasader som viderefører flagget fanges.
 * Test-fakes som implementerer `TexasClient` må beholde parameteret i signaturen sin så lenge det står i grensesnittet; de unntas med [unntatteFilstier].
 *
 * Regelen er ment for de konsumerende appene, ikke for libs selv — `texas`-modulen er definisjonsstedet for det utgåtte parameteret og kjører den derfor ikke på egen kode.
 */
object IngenRewriteAudienceTarget {

    fun brudd(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope.kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            file.kodelinjer().mapNotNull { (linjenummer, kode) ->
                rewriteAudienceTargetRegex.find(kode)?.let { match -> "${file.path}:$linjenummer: ${match.value.trim()}" }
            }
        }

    fun assert(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, unntatteFilstier),
        "Ikke oppgi rewriteAudienceTarget — TexasHttpClient utleder target-formatet av scope-verdien selv. Feil bruk av flagget har tatt ned produksjon to ganger.",
    )

    private val rewriteAudienceTargetRegex = Regex("""\brewriteAudienceTarget\b\s*[:=]""")
}
