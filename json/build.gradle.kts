plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    // Json
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.core)
    api(libs.jackson.module.kotlin)
    implementation(libs.arrow.core)
    implementation(libs.arrow.core.jackson)

    testImplementation(project(":test-common"))
}

// Vi støtter kun Jackson 3 (`tools.jackson.*`). Ingen Jackson 2-artefakter skal ende opp på
// classpath — verken direkte eller transitivt. `com.fasterxml.jackson.core:jackson-annotations`
// er delt mellom Jackson 2 og 3, så den ekskluderes ikke; alt annet under `com.fasterxml.jackson.*`
// ekskluderes per gruppe slik at vi fanger core/databind, module-*, datatype-* og dataformat-*.
configurations.all {
    exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
    exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
    exclude(group = "com.fasterxml.jackson.module")
    exclude(group = "com.fasterxml.jackson.datatype")
    exclude(group = "com.fasterxml.jackson.dataformat")
}

// Fail-loud-sjekk i tillegg til exclude(): hvis Jackson 2-artefakter sniker seg inn via en
// fremtidig avhengighet vi ikke har tenkt på, vil byggesteget under stoppe det før test/publish.
// Vi sjekker hele com.fasterxml.jackson.*-namespacet (core, databind, module, datatype, ...),
// ikke bare et håndplukket sett — det eneste lovlige unntaket er `jackson-annotations`,
// som er delt mellom Jackson 2 og 3.
val tillatteJackson2Artefakter = setOf(
    "com.fasterxml.jackson.core:jackson-annotations",
)
val runtimeArtefakter = configurations.named("runtimeClasspath")
    .flatMap { it.incoming.artifacts.resolvedArtifacts }
val testRuntimeArtefakter = configurations.named("testRuntimeClasspath")
    .flatMap { it.incoming.artifacts.resolvedArtifacts }

val sjekkIngenJackson2 by tasks.registering {
    val runtime = runtimeArtefakter
    val testRuntime = testRuntimeArtefakter
    val tillatt = tillatteJackson2Artefakter
    doLast {
        listOf("runtimeClasspath" to runtime.get(), "testRuntimeClasspath" to testRuntime.get())
            .forEach { (navn, artefakter) ->
                val funnet = artefakter
                    .map { "${it.id.componentIdentifier}".substringBeforeLast(":") }
                    .filter { it.startsWith("com.fasterxml.jackson.") && it !in tillatt }
                check(funnet.isEmpty()) {
                    "Jackson 2-artefakter funnet på $navn: $funnet — bruk tools.jackson (Jackson 3)."
                }
            }
    }
}

tasks.named("check") {
    dependsOn("koverVerify", sjekkIngenJackson2)
}

kover {
    reports {
        verify {
            rule {
                minBound(100)
            }
        }
    }
}

