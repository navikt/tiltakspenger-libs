package no.nav.tiltakspenger.libs.periodisering

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
