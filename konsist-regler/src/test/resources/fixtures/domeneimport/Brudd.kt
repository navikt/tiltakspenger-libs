package fixtures.domene

import arrow.core.Either
import com.eksempel.eksternt.Klient
import fixtures.infra.EnKlient

class Brudd(val e: Either<String, Int>, val k: Klient, val i: EnKlient)
