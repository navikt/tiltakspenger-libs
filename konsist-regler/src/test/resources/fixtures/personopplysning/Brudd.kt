package fixtures.personopplysning

// Data class som markerer seg, men lener seg på den genererte toString-en.
// Kompilatoren er fornøyd, og verdien lekker.
data class LekkerFødselsnummer(val verdi: String) : Personopplysning

// Samme hull gjennom en kategori under rot-interfacet.
data class LekkerArrangørnavn(val verdi: String) : Stedsinformasjon
