package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * En navngitt domenepakke har verken infrastruktur inni seg eller importer fra infrastruktur.
 *
 * Arbeidsdelingen er ofte lest ut av strukturen selv: domenepakken har aldri hatt en `infra`-underpakke, og ingen har skrevet ned at den ikke skal få en.
 * Uten en regel brytes det uten at noen tar beslutningen — en db-mapper for en domenetype ser ut til å høre hjemme ved siden av typen den mapper, og da vokser det fram en `<domenepakke>/infra/`-pakke ingen bestemte seg for.
 * Regelen fanger begge retningene: infrastruktur som flytter inn i domenepakken, og domenepakken som strekker seg ut i infrastrukturen.
 *
 * [InfraImport] er den generelle varianten og sier at kun infra-pakker importerer infra, uten å peke ut noen bestemt pakke.
 * Denne peker ut én pakke, og legger til kravet om at pakken heller ikke har infrastruktur inni seg.
 *
 * Kalleren sender typisk `scopeFromProduction()` og pakkenavnet; alt under pakken regnes med.
 * Er utvalget tomt — feilstavet pakkenavn, eller et scope som peker på feil tre — består begge reglene trivielt, så kjør [assertFinnerDomenepakken] ved siden av dem.
 */
object DomenepakkeUtenInfrastruktur {

    /**
     * `infra` er segmentet appene bruker selv.
     * `infrastruktur` er med fordi libs bruker det (`libs.persistering.infrastruktur`), og et slikt importbehov i en domenepakke er like mye et brudd.
     * Settet er bredere enn [InfraImport.standardInfraSegmenter], som kun kjenner `infra`.
     */
    val standardInfraSegmenter = setOf("infra", "infrastruktur")

    /**
     * Filer som ligger i en infra-underpakke av domenepakken.
     *
     * Prefikset trekkes fra før segmentene leses, slik at en domenepakke som selv heter noe med `infra` ikke flagger alt den inneholder.
     * Det finnes ingen whitelist her med vilje: en fil som ligger i feil pakke flyttes, og det er ingen designbeslutning å utsette.
     */
    fun bruddInfraUnderpakke(
        scope: KoScope,
        domenepakke: String,
        ekstraInfraSegmenter: Set<String> = emptySet(),
    ): List<String> {
        val infraSegmenter = standardInfraSegmenter + ekstraInfraSegmenter
        return scope.kildefiler()
            .map { file -> file to file.packagee?.name.orEmpty() }
            .filter { (_, pakke) -> pakke.erLikEllerUnder(domenepakke) }
            .filter { (_, pakke) -> pakke.removePrefix(domenepakke).harSegmentI(infraSegmenter) }
            .map { (file, pakke) -> "${file.path}: ligger i $pakke" }
    }

    fun assertIngenInfraUnderpakker(
        scope: KoScope,
        domenepakke: String,
        ekstraInfraSegmenter: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        bruddInfraUnderpakke(scope, domenepakke, ekstraInfraSegmenter),
        "Infrastruktur hører hjemme under `infra`, ikke i domenepakken `$domenepakke`. Mapping av en domenetype er infrastruktur, selv om typen den mapper er domene.",
    )

    /**
     * Filer i domenepakken som importerer infrastruktur.
     *
     * [unntatteFilstier] er sti-suffikser som unntas, for filer der oppryddingen er en designendring og ikke en mekanisk flytting — sett en kommentar om hvorfor på aktiveringsstedet, og hold whitelisten ærlig med [assertWhitelistenErRyddet].
     * Unntaket gjelder kun importene: en fil som i tillegg ligger i en infra-underpakke flagges fortsatt av [bruddInfraUnderpakke].
     */
    fun bruddInfraImport(
        scope: KoScope,
        domenepakke: String,
        unntatteFilstier: Set<String> = emptySet(),
        ekstraInfraSegmenter: Set<String> = emptySet(),
    ): List<String> {
        val infraSegmenter = standardInfraSegmenter + ekstraInfraSegmenter
        return scope.kildefiler()
            .filter { file -> file.packagee?.name.erLikEllerUnder(domenepakke) }
            .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
            .flatMap { file ->
                file.imports
                    .filter { import -> import.name.harSegmentI(infraSegmenter) }
                    .map { import -> "${file.path}: importerer ${import.name}" }
            }
    }

    fun assertIngenInfraImport(
        scope: KoScope,
        domenepakke: String,
        unntatteFilstier: Set<String> = emptySet(),
        ekstraInfraSegmenter: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        bruddInfraImport(scope, domenepakke, unntatteFilstier, ekstraInfraSegmenter),
        "`$domenepakke` er domenekode og skal ikke avhenge av infrastruktur. Snu avhengigheten: la infrastrukturen sende inn det domenet trenger.",
    )

    /** Vakt mot at reglene over er grønne fordi ingen filer ble funnet i [domenepakke] — se [assertSkanningenTraff]. */
    fun assertFinnerDomenepakken(
        scope: KoScope,
        domenepakke: String,
        minstAntallFiler: Int,
    ) = assertSkanningenTraff(
        antall = scope.kildefiler().count { file -> file.packagee?.name.erLikEllerUnder(domenepakke) },
        minstAntall = minstAntallFiler,
        hva = "filer i $domenepakke",
    )
}
