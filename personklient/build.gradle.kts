import org.jetbrains.kotlin.storage.CacheResetOnProcessCanceled.enabled

tasks.named<Jar>("jar") {
    enabled = false
    archiveBaseName.set("personklient")
}

tasks.withType<Javadoc> { enabled = false }
tasks.withType<PublishToMavenRepository> { enabled = false }
