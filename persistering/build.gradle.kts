plugins {
    id("tiltakspenger-lib-conventions")
}

tasks.withType<Jar> {
    enabled = false
    archiveBaseName.set("persistering")
}
