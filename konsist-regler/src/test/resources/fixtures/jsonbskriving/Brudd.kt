package fixtures.jsonbskriving

import org.postgresql.util.PGobject

private val pakketParameter = """INSERT INTO tabell (data) VALUES (to_jsonb(:payload))"""
private val pakketMedCast = """INSERT INTO tabell (data) VALUES (to_json(:payload::jsonb))"""
private val feilCast = """INSERT INTO tabell (data) VALUES (:payload::json)"""
private val feilCastMedStoreBokstaver = """INSERT INTO tabell (data) VALUES (:payload::JSON)"""
private val pgObject = PGobject()
