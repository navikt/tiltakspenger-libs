# tiltakspenger-libs

Libraries are published to githubs maven repo.

Example usage:

```kotlin
repositories {
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}
dependencies {
    implementation("com.github.navikt.tiltakspenger-libs:person-dtos:0.0.7")
}
```

felles bibliotek for snacks i tiltakspenger repoer
