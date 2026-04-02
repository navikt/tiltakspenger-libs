package no.nav.tiltakspenger.libs.json

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.matchers.equals.shouldBeEqual
import org.junit.jupiter.api.Test

internal class JacksonArrowModuleTest {
    @Test
    fun serialize() {
        serialize(nonEmptyListOf("cake")) shouldBeEqual """["cake"]"""
        serialize(InnerNel(inner = nonEmptyListOf("cake"))) shouldBeEqual """{"inner":["cake"]}"""
        deserialize<NonEmptyList<String>>("""["cake"]""") shouldBeEqual nonEmptyListOf("cake")
        // TODO jah: Se TODO under.
        // deserialize<NonEmptyList<String>>("""{"inner":["cake"]}""") shouldBeEqual InnerNel(inner = nonEmptyListOf("cake"))
    }
}

private class InnerNel(
    // TODO jah: Bytt denne til NonEmptyList etter kotlin 2.2.20 - https://youtrack.jetbrains.com/issue/KT-52706/Bad-signature-for-generic-value-classes-with-substituted-type-parameter
    val inner: List<String>,
)
