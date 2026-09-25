plugins {
    id("no.nav.tiltakspenger.bibliotek")
    id("no.nav.tiltakspenger.dekning")
}

dependencies {
    // KafkaConfig er del av public API her: AvroKafkaConfig pakker den inn og delegerer consumer-configen til den.
    api(project(":kafka"))

    testImplementation(project(":test-common"))
}
