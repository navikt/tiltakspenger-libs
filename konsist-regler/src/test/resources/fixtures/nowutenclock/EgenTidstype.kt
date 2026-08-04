package fixtures.nowutenclock

// En egen tidstype med samme no-arg-mønster som java.time-typene.
// Standardsettet kjenner den ikke, så den flagges først når repoet legger typenavnet til.
class Virkedag {
    fun nåtid(): Virkedag = Virkedag.now()

    companion object {
        fun now(): Virkedag = Virkedag()
    }
}
