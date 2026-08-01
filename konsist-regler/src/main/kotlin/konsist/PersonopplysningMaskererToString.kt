package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * En type som markerer seg som personopplysning må maskere `toString()` selv.
 *
 * `Personopplysning` redeklarerer `toString()` som abstrakt, så kompilatoren krever allerede en implementasjon av vanlige klasser, value classes og objects.
 * Denne regelen dekker det ene hullet kompilatoren ikke ser: en `data class` tilfredsstiller kravet med sin **genererte** `toString()`, som skriver ut alle feltene i klartekst.
 * Da er markeringen der, kompilatoren er fornøyd, og verdien lekker i første beste logglinje.
 *
 * Regelen krever derfor at en `data class` som markerer seg deklarerer sin egen `toString()`.
 * Den sjekker ikke hva implementasjonen gjør — at en deklarert `toString()` faktisk maskerer er kodegjennomgangens jobb.
 *
 * [markører] er navnene som regnes som markering, og dekker både rot-interfacet og kategoriene under det.
 * Kalleren sender typisk `scopeFromProduction()`.
 */
object PersonopplysningMaskererToString {

    /** Rot-interfacet og kategoriene under det, slik de heter i `tiltakspenger-libs:common`. */
    val standardMarkører = setOf("Personopplysning", "Stedsinformasjon")

    fun brudd(
        scope: KoScope,
        markører: Set<String> = standardMarkører,
    ): List<String> = scope
        .kildefiler()
        .asSequence()
        .flatMap { file -> file.classes(includeNested = true) }
        .filter { klasse -> klasse.hasDataModifier }
        .filter { klasse -> klasse.parents().any { parent -> parent.name in markører } }
        .filterNot { klasse -> klasse.functions().any { funksjon -> funksjon.name == "toString" } }
        .map { klasse -> "${klasse.containingFile.path}: ${klasse.name}" }
        .toList()

    fun assert(
        scope: KoScope,
        markører: Set<String> = standardMarkører,
    ) = assertIngenBrudd(
        brudd(scope, markører),
        "En data class som markerer seg som personopplysning må deklarere sin egen toString() — den genererte skriver ut verdien i klartekst.",
    )
}
