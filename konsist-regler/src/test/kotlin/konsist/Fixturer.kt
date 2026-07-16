package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import java.nio.file.Path

/**
 * Fixturene ligger i test-resources, ikke i testkildene: bruddfixturene ville ellers trippet repo-reglene selv, og noen kompilerer ikke.
 * Konsist trenger bare at filene parser, ikke at de kompilerer.
 * Scopet lages fra processResources-output under `build/`, siden reglene bevisst filtrerer bort `.kt`-filer under `src/<sourceSet>/resources` (se `kildefiler`).
 */
internal fun fixtureScope(katalog: String): KoScope =
    Konsist.scopeFromExternalDirectory(fixturesti(katalog).toString())

internal fun fixturesti(katalog: String): Path =
    Path.of(System.getProperty("user.dir"), "build", "resources", "test", "fixtures", katalog)
