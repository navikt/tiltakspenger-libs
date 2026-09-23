package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Tiltakstypene som gir rett til tiltakspenger.
 *
 * Dette er den granulære aksen i domenet: her diskriminerer vi, og derfor er den en enum.
 * Tiltakstyper som ikke gir rett bæres som `String`, fordi vi aldri trenger å skille dem fra hverandre.
 *
 * Enumen bærer bevisst ikke noe visningsnavn.
 * Navnet på tiltaket kommer alltid fra kilden (eller fra saksbehandler ved manuelt registrert søknad), aldri utledet herfra.
 *
 * Kartleggingen fra kildesystemenes koder (Arena, Komet, Team Tiltak) til disse verdiene er mapping og hører hjemme i infrastrukturen, ikke her.
 *
 * Typen skal aldri brukes til å lese fra eller skrive til en database.
 * Konsumentene eier sine egne databasetyper og mapper til og fra dem, slik `TiltakstypeSomGirRettDb` i saksbehandling-api gjør.
 * Det er den typen som er bundet av lagrede rader — denne er fri til å endre seg med domenet.
 *
 * Doc: https://confluence.adeo.no/pages/viewpage.action?pageId=653427539
 */
enum class TiltakstypeSomGirRett {
    ARBEIDSFORBEREDENDE_TRENING,
    ARBEIDSMARKEDSOPPLAERING,
    ARBEIDSRETTET_REHABILITERING,
    ARBEIDSTRENING,
    AVKLARING,
    DIGITAL_JOBBKLUBB,
    ENKELTPLASS_AMO,
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG,
    FAG_OG_YRKESOPPLAERING,
    FORSØK_OPPLÆRING_LENGRE_VARIGHET,
    GRUPPE_AMO,
    GRUPPE_VGS_OG_HØYERE_YRKESFAG,
    HØYERE_UTDANNING,
    HOYERE_YRKESFAGLIG_UTDANNING,
    INDIVIDUELL_JOBBSTØTTE,
    INDIVIDUELL_KARRIERESTØTTE_UNG,
    JOBBKLUBB,
    NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
    OPPFØLGING,
    STUDIESPESIALISERING,
    UTVIDET_OPPFØLGING_I_NAV,
    UTVIDET_OPPFØLGING_I_OPPLÆRING,
}
