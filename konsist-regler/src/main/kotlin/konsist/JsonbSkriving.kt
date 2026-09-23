package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Et jsonb-parameter skrives som `:navn::jsonb` uten innpakning, og serialiseringen går aldri utenom Jackson via pgjdbc sin `PGobject`.
 *
 * Kotliquery sender parameteren som tekst, og det er casten som konverterer.
 * Skriver du `to_jsonb(:navn)` uten cast, får du hele json-dokumentet escapet ned i én json-streng — `to_jsonb('{"a":1}'::text)` gir `"{\"a\":1}"`, ikke `{"a": 1}`.
 * Det feiler ikke ved skriving; det feiler først når Jackson skal lese raden tilbake.
 *
 * Innpakningene `to_jsonb(:navn::jsonb)`, `to_json(:navn::jsonb)` og `to_jsonb(:navn::json)` er funksjonelt like den bare casten — `to_jsonb` på noe som allerede er json eller jsonb er identiteten — men de får innpakningen til å se ut som det bærende leddet.
 * En som senere rydder bort `::jsonb` i tro på at `to_jsonb` konverterer, skriver da stille escapede strenger i kolonnen.
 * Derfor forbys enhver innpakning rundt et bind-parameter, og `::jsonb` kreves framfor `::json`.
 * `to_jsonb(kolonne)` på en ekte kolonneverdi er noe helt annet og fortsatt lov — det er kun `(` etterfulgt av `:` som fanges.
 *
 * Deteksjonen er tekstbasert: SQL-mønstrene leses fra [no.nav.tiltakspenger.libs.konsist.kodelinjerMedStrenger] siden SQL-en bor i strengliteraler, mens `PGobject` leses fra [no.nav.tiltakspenger.libs.konsist.kodelinjer], der strenger er maskert — typenavnet er kode, ikke SQL.
 * Kommentarlinjer teller ikke i noen av dem, så dokumentasjonen får vise mønsteret den advarer mot.
 * SQL-mønstrene leses uavhengig av store og små bokstaver; `PGobject` gjør det ikke, siden `pgObject` er et helt vanlig variabelnavn.
 *
 * Kalleren sender typisk `scopeFromProduction()`.
 * Et repo uten treff består begge reglene trivielt, så kjør [assertFinnerJsonbParametre] ved siden av dem, og hold en eventuell whitelist ærlig med [assertWhitelistenErRyddet].
 */
object JsonbSkriving {

    /** Innpakning rundt et bind-parameter, og `::json` der vi mener `::jsonb`. */
    fun bruddBarCast(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope.kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            file.kodelinjerMedStrenger().flatMap { (linjenummer, kode) ->
                (innpakketParameter.findAll(kode) + jsonFramforJsonb.findAll(kode))
                    .map { treff -> "${file.path}:$linjenummer: ${treff.value.trim()}" }
                    .toList()
            }
        }

    fun assertBarCast(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        bruddBarCast(scope, unntatteFilstier),
        "Skriv jsonb-parametre som `:navn::jsonb`. Det er casten som konverterer — `to_jsonb(:navn)` uten cast gir en escapet json-streng, og en innpakning rundt casten skjuler hvem som gjør jobben.",
    )

    /**
     * `PGobject` er pgjdbc sin konvolutt for en verdi driveren ikke kjenner typen til.
     *
     * Den er forbudt fordi den er usynlig for konvensjonen om at rene mappinger skal ha en enhetstest som pinner json-en.
     * En `toPGObject(value: Any?)`-hjelper som kaller `objectMapper` rett fra repoet har ingen navngitt type å pinne, og et nytt felt endrer da formatet på disk uten at noe slår ut.
     */
    fun bruddPGobject(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope.kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            file.kodelinjer().mapNotNull { (linjenummer, kode) ->
                "${file.path}:$linjenummer: bruker PGobject".takeIf { pgObject.containsMatchIn(kode) }
            }
        }

    fun assertIngenPGobject(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        bruddPGobject(scope, unntatteFilstier),
        "Serialiser til jsonb gjennom en navngitt `*DbJson`-type med `toDbJson()`/`fromDbJson`, og les med `stringOrNull`. Da kan mappingen pinnes i en enhetstest.",
    )

    /** Vakt mot at reglene over er grønne fordi skanningen ikke fant databaselaget — se [assertSkanningenTraff]. */
    fun assertFinnerJsonbParametre(scope: KoScope, minstAntallFiler: Int) = assertSkanningenTraff(
        antall = scope.kildefiler().count { file ->
            file.kodelinjerMedStrenger().any { (_, kode) -> jsonbCast.containsMatchIn(kode) }
        },
        minstAntall = minstAntallFiler,
        hva = "filer med jsonb-parametre",
    )

    /**
     * `to_json(`/`to_jsonb(` rett foran et bind-parameter.
     * Fanger både innpakningene som kun er redundante og `to_jsonb(:navn)` uten cast, som er den farlige.
     */
    private val innpakketParameter = Regex("""to_jsonb?\(\s*:\w+""", RegexOption.IGNORE_CASE)

    /**
     * `::json` der vi mener `::jsonb`.
     * `\w` er ASCII i JVM-regex, som er riktig her: kotliquery leser navngitte parametre med samme klasse, så `:søknad` er uansett ikke et gyldig parameternavn.
     */
    private val jsonFramforJsonb = Regex(""":\w+::json(?!b)""", RegexOption.IGNORE_CASE)

    /** Selve casten, som er det [assertFinnerJsonbParametre] teller for å se at den ser på databaselaget. */
    private val jsonbCast = Regex("""::jsonb\b""", RegexOption.IGNORE_CASE)

    private val pgObject = Regex("""\bPGobject\b""")
}
