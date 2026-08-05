plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.cyclonedx.gradle.plugin)
    // Gjør versjonskatalogens type-sikre accessors (`libs.*`) tilgjengelige i de prekompilerte skript-pluginene.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
