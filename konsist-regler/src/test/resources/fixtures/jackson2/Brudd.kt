package fixtures.jackson2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

class Brudd(val mapper: ObjectMapper, val modul: KotlinModule)
