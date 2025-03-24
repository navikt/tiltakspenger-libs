package no.nav.tiltakspenger.libs.common

import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Truncater til mikrosekunder for å være kompatibel med PostgreSQL.
 */
fun nå(clock: Clock): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)
