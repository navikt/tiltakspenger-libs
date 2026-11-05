package fixtures.expressionbody

fun overFlereLinjer(a: Int) = listOf(
    a,
    a,
)

class Klasse {
    fun nestet(): Int = when {
        true -> 1
        else -> 2
    }
}
