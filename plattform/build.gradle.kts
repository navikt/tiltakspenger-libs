/**
 * BOM-en for tiltakspenger-flåten — laget versjonskatalogen ikke kan være.
 *
 * En versjonskatalog deklarerer bare hvilken versjon *vi* skriver; den sier ingenting om hva en avhengighet drar inn bak ryggen vår.
 * Constraints her virker transitivt, på samme måte som `dependencyManagement` i Maven, og treffer også artefakter ingen av oss deklarerer.
 *
 * Konsumeres i app-repoet med én linje, og da faller versjonen bort fra alle libs-koordinatene:
 * ```
 * implementation(platform("com.github.navikt.tiltakspenger-libs:plattform:<versjon>"))
 * implementation("com.github.navikt.tiltakspenger-libs:common")
 * ```
 */

plugins {
    `java-platform`
    id("tiltakspenger.publisering")
}

// Uten denne kan en java-platform kun deklarere constraints, ikke importere andre BOM-er.
javaPlatform {
    allowDependencies()
}

// Publiserte libs-moduler, utledet fra prosjektstrukturen framfor en navneliste.
// En navneliste ville mistet en ny modul stille; her er alt som er en bladmodul med automatisk.
// Kun navnene leses, ikke prosjektenes tilstand, så ingen av dem trenger å være evaluert først.
//
// TODO jah: Heuristikken «bladmodul = publisert» er riktig i dag, men skjør på to måter.
// Legger noen en modul ett nivå dypere (f.eks. under `httpklient-infrastruktur`), får forelderen barn, faller ut av BOM-en,
// og hvert app-repo som skriver koordinaten uten versjon feiler med «no version specified».
// En bladmodul som ikke publiserer, ville motsatt gitt en constraint mot en koordinat som aldri finnes.
// Vurder å utlede fra prosjektene som faktisk har `tiltakspenger.publisering`, eller legg på en vakt som sammenligner de to settene.
val bibliotekmoduler =
    rootProject.subprojects
        .filter { modul -> modul.childProjects.isEmpty() }
        .map { modul -> modul.name }
        .filterNot { navn -> navn == project.name || navn == "versjonskatalog" }
        .sorted()

dependencies {
    // BOM-er vi videreformidler til konsumentene.
    // Netty: r2dbc-postgresql/reactor-netty drar inn 4.1.x mens ktor-server-netty bruker 4.2.x, og uten justering havner
    // begge på classpath med duplikate baseklasser (ByteToMessageDecoder m.fl.), som med `-cp lib/*` lastes i feil
    // rekkefølge og brekker HTTP-pipelinen.
    //
    // TODO jah: Dette pinnet står i konflikt med `persistering-suspending`, som bevisst pinner netty 4.1-linja for r2dbc/reactor-netty.
    // Constraintet her vinner, så en app som tar i bruk den modulen vil kjøre reactor-netty mot en Netty-linje den ikke er bygget for.
    // Latent i dag, siden ingen app-repoer bruker `persistering-suspending` — avklar før den får sin første konsument.
    api(platform(libs.netty42.bom))
    // Jackson 2 brukes ikke direkte — koden vår er på jackson3 (tools.jackson) — men kommer transitivt via Confluent,
    // tms-bibliotekene og com.auth0. Bom-en løfter dem over de patchede versjonene.
    api(platform(libs.jackson2.bom))

    constraints {
        bibliotekmoduler.forEach { modul ->
            api("com.github.navikt.tiltakspenger-libs:$modul:${project.version}")
        }

        // Confluent publiserer sin egen fork av kafka-clients som `<versjon>-ccs`. Den taper ikke konfliktoppløsningen
        // mot Apache-versjonen — Gradle leser "8.1.1-ccs" som høyere enn "4.3.1" — så uten `strictly` er det
        // Confluent-forken som havner i imaget. Den er bygd på Kafka 4.1 og drar inn den avviklede `org.lz4:lz4-java`
        // 1.8.0, som har både out-of-bounds-lesing (GHSA-vqf4-7m7x-wgfc) og en informasjonslekkasje i den trygge
        // dekomprimereren (GHSA-cmp6-m4wj-q63q) — sistnevnte uten fiks på de koordinatene.
        api(libs.kafka.clients) {
            version { strictly(libs.versions.kafka.get()) }
        }
        // Apache kafka-clients drar inn lz4-java 1.10.2, der de native XXHash-implementasjonene kan krasje JVM-en
        // på ugyldige byte-intervaller (GHSA-xx22-p4ch-683r).
        api(libs.lz4.java)
        // r2dbc-postgresql drar inn scram 3.2 med auth-nedgraderingssårbarhet (GHSA-p9jg-fcr6-3mhf, patchet i 3.3).
        api(libs.scram.client)
        api(libs.scram.common)
    }
}

publishing {
    publications {
        create<MavenPublication>("plattform") {
            artifactId = project.name
            from(components["javaPlatform"])
        }
    }
}
