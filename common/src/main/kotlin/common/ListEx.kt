package no.nav.tiltakspenger.libs.common

fun <T, K> List<T>.nonDistinctBy(selector: (T) -> K): List<T> {
    return this.groupBy(selector).values.filter { it.size > 1 }.flatten()
}
