package fixtures.internalmodifier

internal typealias Saksnummer = String

internal const val TAK = 10

internal val terskel = 3

internal fun beregn(beløp: Int): Int = beløp * TAK

/**
 * Erklæring med KDoc, der Konsist plasserer erklæringen på KDoc-ens første linje.
 * Ordet internal står også her i prosa, uten å være en modifikator.
 */
internal fun beregnDokumentert(beløp: Int): Int = beløp

internal class Beregner {

    internal val sats = 1

    internal fun kjør(): Int = sats

    internal class Hjelper
}

internal interface Regel

internal object Registeret

internal enum class Status { NY, GAMMEL }

internal data class Rad(val id: String)

class MedInternalKonstruktør internal constructor(val id: String)

class MedInternalSetter {
    var tilstand: Int = 0
        internal set
}
