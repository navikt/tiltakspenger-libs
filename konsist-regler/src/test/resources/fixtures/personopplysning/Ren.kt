package fixtures.personopplysning

// Value class som markerer seg og maskerer selv.
// Kompilatoren krever allerede overstyringen her, så regelen har ingenting å si.
@JvmInline
value class Arrangørnavn(val verdi: String) : Stedsinformasjon {
    override val begrunnelse: String get() = "Peker ut hvor personen møter opp."
    override fun toString(): String = "*****"
}

// Data class som markerer seg, men deklarerer sin egen toString.
// Dette er mønsteret Fnr bruker, og skal ikke flagges.
data class Fødselsnummer(val verdi: String) : Personopplysning {
    override val begrunnelse: String get() = "Direkte identifiserende."
    override fun toString(): String = "***********"
}

// Data class uten markering.
// Den er ikke regelens bord, selv om den har et felt som ser sensitivt ut.
data class Adresse(val gate: String)
