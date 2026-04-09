plugins {
    base
}

tasks.withType<Jar> {
    enabled = false
    archiveBaseName.set("persistering")
}
