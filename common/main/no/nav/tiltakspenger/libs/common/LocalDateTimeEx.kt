package no.nav.tiltakspenger.libs.common

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Truncater til mikrosekunder for å være kompatibel med PostgreSQL.
 */
fun nå(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
