@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:class-naming")

package fixtures.backticks

fun `testnavn med mellomrom er greit`() = Unit

val `object` = 1

// Kommentar med `kodespenn` skal ikke flagges.

/**
 * KDoc med `kodespenn` skal heller ikke flagges.
 */
val tekst = "streng med `kodespenn`"

val flerlinje = """
    markdown med `kodespenn` i en flerlinjestreng
"""

val medTrailing = 1 // trailing-kommentar med `kodespenn`
