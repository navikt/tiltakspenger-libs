package no.nav.tiltakspenger.libs.dato

import java.time.format.DateTimeFormatter
import java.util.Locale

val localeNorsk: Locale = Locale
    .Builder()
    .setLanguage("no")
    .setRegion("NO")
    .build()

val norskDatoFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", localeNorsk)
val norskTidspunktFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMMM yyyy HH:mm:ss", localeNorsk)
val norskDatoOgTidFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", localeNorsk)
val norskDatoMedPunktumFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", localeNorsk)
val norskUkedagOgDatoUtenÅrFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d. MMMM", localeNorsk)

val localeEngelsk: Locale = Locale
    .Builder()
    .setLanguage("en")
    .setRegion("UK")
    .build()

val engelskDatoFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", localeEngelsk)
val engelskTidspunktFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm:ss", localeEngelsk)
val engelskDatoOgTidFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", localeEngelsk)
val engelskDatoMedPunktumFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", localeEngelsk)
val engelskUkedagOgDatoUtenÅrFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", localeEngelsk)
