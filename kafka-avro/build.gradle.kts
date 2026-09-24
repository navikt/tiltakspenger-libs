plugins {
    id("tiltakspenger.bibliotek")
    id("tiltakspenger.dekning")
}

dependencies {
    // KafkaConfig er del av public API her: AvroKafkaConfig pakker den inn og delegerer consumer-configen til den.
    api(project(":kafka"))

    testImplementation(project(":test-common"))
}
