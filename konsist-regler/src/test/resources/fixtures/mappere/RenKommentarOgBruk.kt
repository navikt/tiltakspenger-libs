package fixtures.mappere

import tools.jackson.databind.json.JsonMapper

// JsonMapper.builder( i en linjekommentar skal ikke flagges.
/**
 * KDoc som nevner jacksonObjectMapper() skal heller ikke flagges.
 * Det samme gjelder jackson3 { i en blokkommentar.
 */
fun bruk(mapper: JsonMapper): String {
    return mapper.writeValueAsString("verdi") // trailing kommentar med ObjectMapper( skal ikke flagges
}
