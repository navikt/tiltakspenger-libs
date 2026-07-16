package fixtures.mappere

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonObjectMapper

val lokalMapper: JsonMapper = JsonMapper.builder().build()
val annenLokalMapper = jacksonObjectMapper()
