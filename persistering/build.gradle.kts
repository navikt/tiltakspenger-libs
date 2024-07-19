import org.jetbrains.kotlin.storage.CacheResetOnProcessCanceled.enabled

tasks.named<Jar>("jar") {
    enabled = false
    archiveBaseName.set("persistering")
}

tasks.withType<Javadoc> { enabled = false }
tasks.withType<PublishToMavenRepository> { enabled = false }
