package no.nav.tiltakspenger.libs.periodisering

import java.time.format.DateTimeFormatter
import java.util.Locale

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

val norskDatoMedPunktumFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        "dd.MM.yyyy",
        Locale
            .Builder()
            .setLanguage("no")
            .setRegion("NO")
            .build(),
    )

val norskUkedagOgDatoUtenÅrFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "EEEE d. MMMM",
    Locale.Builder()
        .setLanguage("no")
        .setRegion("NO")
        .build(),
)
