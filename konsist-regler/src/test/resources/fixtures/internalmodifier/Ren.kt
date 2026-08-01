package fixtures.internalmodifier

// Ordet «internal» i en kommentar er ikke en modifikator.
private const val INTERNAL_HEADER = "X-Internal-Id"

private val internalId = 1

fun beregnRent(beløp: Int): Int = beløp * internalId

class RenBeregner {

    private val sats = 1

    fun kjør(): Int = sats + INTERNAL_HEADER.length

    private class Hjelper
}

interface RenRegel

object RenRegisteret

private class RenPrivat {
    var tilstand: Int = 0
        private set
}
