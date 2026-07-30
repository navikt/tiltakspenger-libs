package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Kildesystemet tiltaksdeltakelsen kommer fra.
 *
 * Typen skal aldri brukes til å lese fra eller skrive til en database, og heller ikke serialiseres direkte ut på et API.
 * Konsumentene eier sine egne databasetyper og DTO-er og mapper til og fra dem.
 */
enum class Tiltakskilde {
    Arena,
    Komet,
    TeamTiltak,
}
