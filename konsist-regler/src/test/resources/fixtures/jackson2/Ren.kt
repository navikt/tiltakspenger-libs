package fixtures.jackson2

import com.fasterxml.jackson.annotation.JsonIgnore
import tools.jackson.databind.ObjectMapper

class Ren(@JsonIgnore val mapper: ObjectMapper)
