package no.nav.tiltakspenger.libs.periodisering

import java.time.format.DateTimeFormatter
import java.util.*

val norskDatoFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        "d. MMMM yyyy",
        Locale
            .Builder()
            .setLanguage("no")
            .setRegion("NO")
            .build(),
    )

val norskTidspunktFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        "d. MMMM yyyy HH:mm:ss",
        Locale
            .Builder()
            .setLanguage("no")
            .setRegion("NO")
            .build(),
    )
