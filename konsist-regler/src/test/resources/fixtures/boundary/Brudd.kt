package fixtures.domene

data class NoeDTO(val id: String)

class SvarResponse(val ok: Boolean)

// Et suffiks repoet selv regner som boundary; standardsettet kjenner det ikke.
class OpprettKommando(val id: String)
