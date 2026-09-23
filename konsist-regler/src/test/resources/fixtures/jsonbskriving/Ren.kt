package fixtures.jsonbskriving

/**
 * Dokumentasjonen får vise mønsteret den advarer mot: skriv `:navn::jsonb`, ikke `to_jsonb(:navn)` eller `:navn::json`.
 * Den får også nevne at PGobject er forbudt.
 */
private val barCast = """INSERT INTO tabell (data) VALUES (:payload::jsonb)"""

// Kolonnefunksjonen er noe helt annet enn en innpakket parameter, og er fortsatt lov.
private val kolonnefunksjon = """SELECT to_jsonb(data) FROM tabell"""
