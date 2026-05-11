package no.nav.tiltakspenger.libs.json

import io.kotest.matchers.shouldBe

/**
 * Asserterer både eksakt JSON-format og round-trip:
 *  - `serialize(value) == expectedJson`
 *  - `deserialize<T>(expectedJson) == value`
 *
 * Round-trip alene er ikke nok — symmetrisk brutte sider av serialize/deserialize kan henge sammen uten at JSON-formatet er det vi tror.
 */
internal inline fun <reified T : Any> roundTrip(value: T, expectedJson: String) {
    serialize(value) shouldBe expectedJson
    deserialize<T>(expectedJson) shouldBe value
}

/**
 * Round-trip for `List<T>` via [List.serialize] og [deserializeList].
 */
internal inline fun <reified T> roundTripList(value: List<T>, expectedJson: String) {
    value.serialize() shouldBe expectedJson
    deserializeList<T>(expectedJson) shouldBe value
}

/**
 * Round-trip for `Map<K, V>` via [serialize] og [deserializeMap].
 */
internal inline fun <reified K, reified V> roundTripMap(value: Map<K, V>, expectedJson: String) {
    serialize(value) shouldBe expectedJson
    deserializeMap<K, V>(expectedJson) shouldBe value
}
