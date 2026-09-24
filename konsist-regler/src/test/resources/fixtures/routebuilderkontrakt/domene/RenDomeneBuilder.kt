package fixtures.routebuilderkontrakt.domene

import io.kotest.matchers.shouldBe

// En domene-builder matcher filnavnmønsteret, men ligger utenfor route-katalogene og er derfor utenfor kontrakten.
interface RenDomeneBuilder {

    fun byggDomeneobjektReturnerRespons(forventetStatus: Int?): String {
        forventetStatus shouldBe null
        return "domene"
    }
}
