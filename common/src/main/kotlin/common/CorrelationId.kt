package no.nav.tiltakspenger.libs.common

import java.util.UUID

@JvmInline
value class CorrelationId(val value: String) {
    override fun toString() = value

    companion object {
        fun generate(): CorrelationId = CorrelationId(UUID.randomUUID().toString())
    }
}
