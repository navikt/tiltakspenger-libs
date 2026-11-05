package fixtures.expressionbody

fun énLinje(a: Int = 1, f: (Int) -> Int = { it }): Map<String, List<Int>> = mapOf("a" to listOf(f(a)))

fun signaturOverFlereLinjer(
    a: Int,
    b: Int,
): Int = a + b

fun linjeskiftEtterLikhetstegn(a: Int): Int =
    a + 1

fun blokk(a: Int): List<Int> {
    return listOf(
        a,
        a,
    )
}

/**
 * KDoc med `fun` og `=` skal ikke forstyrre.
 */
@Suppress("unused")
fun medAnnotasjon() = 1
