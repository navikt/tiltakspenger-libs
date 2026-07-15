package no.nav.tiltakspenger.libs.httpklient

/**
 * JSON som konsumenten allerede har serialisert selv — typisk fordi nøyaktig payload skal persisteres sammen med resultatet (utbetaling, pdfgen, datadeling).
 * Sendes verbatim og re-serialiseres aldri.
 * En egen type (i stedet for en `String`-overload) fjerner den gamle fella der `post(uri, body: Any)` serialiserte mens `post(uri, body: String)` sendte verbatim — og semantikken ble avgjort av variabelens statiske type på call site.
 */
@JvmInline
value class SerialisertJson(val json: String)
