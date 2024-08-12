package no.nav.tiltakspenger.libs.personklient.pdl.dto

enum class GtType {
    KOMMUNE,
    BYDEL,
    UTLAND,
    UDEFINERT,
}

data class GeografiskTilknytning(
    val gtType: GtType,
    val gtKommune: String?,
    val gtBydel: String?,
    val gtLand: String?,
    val regel: String,
)
