plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    // KafkaConfig er del av public API her: AvroKafkaConfig pakker den inn og delegerer consumer-configen til den.
    api(project(":kafka"))

    testImplementation(project(":test-common"))
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

tasks.check {
    dependsOn(tasks.koverVerify)
}
