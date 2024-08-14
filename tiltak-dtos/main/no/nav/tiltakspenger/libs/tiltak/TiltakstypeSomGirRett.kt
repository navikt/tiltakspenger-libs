package no.nav.tiltakspenger.libs.tiltak

import java.lang.RuntimeException

enum class TiltakstypeSomGirRett {
    /** ARBEIDSFORBEREDENDE_TRENING i Komet per 2024-08-14  */
    ARBEIDSFORBEREDENDE_TRENING,

    /** ARBEIDSRETTET_REHABILITERING i Komet per 2024-08-14  */
    ARBEIDSRETTET_REHABILITERING,
    ARBEIDSTRENING,

    /** AVKLARING i Komet per 2024-08-14  */
    AVKLARING,

    /** DIGITALT_OPPFOLGINGSTILTAK i Komet per 2024-08-14 */
    DIGITAL_JOBBKLUBB,
    ENKELTPLASS_AMO,
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG,

    FORSØK_OPPLÆRING_LENGRE_VARIGHET,

    /** GRUPPE_ARBEIDSMARKEDSOPPLAERING i Komet per 2024-08-14 */
    GRUPPE_AMO,

    /** GRUPPE_FAG_OG_YRKESOPPLAERING i Komet per 2024-08-14 */
    GRUPPE_VGS_OG_HØYERE_YRKESFAG,
    HØYERE_UTDANNING,
    INDIVIDUELL_JOBBSTØTTE,
    INDIVIDUELL_KARRIERESTØTTE_UNG,

    /** JOBBKLUBB i Komet per 2024-08-14 */
    JOBBKLUBB,

    /** OPPFOLGING i Komet per 2024-08-14 */
    OPPFØLGING,
    UTVIDET_OPPFØLGING_I_NAV,
    UTVIDET_OPPFØLGING_I_OPPLÆRING,
    ;

    companion object {
        fun fraString(value: String): TiltakstypeSomGirRett {
            return when (value) {
                "ARBEIDSFORBEREDENDE_TRENING" -> TiltakstypeSomGirRett.ARBEIDSFORBEREDENDE_TRENING
                "ARBEIDSRETTET_REHABILITERING" -> TiltakstypeSomGirRett.ARBEIDSRETTET_REHABILITERING
                "ARBEIDSTRENING" -> TiltakstypeSomGirRett.ARBEIDSTRENING
                "AVKLARING" -> TiltakstypeSomGirRett.AVKLARING
                "DIGITAL_JOBBKLUBB" -> TiltakstypeSomGirRett.DIGITAL_JOBBKLUBB
                "ENKELTPLASS_AMO" -> TiltakstypeSomGirRett.ENKELTPLASS_AMO
                "ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG" -> TiltakstypeSomGirRett.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG
                "FORSØK_OPPLÆRING_LENGRE_VARIGHET" -> TiltakstypeSomGirRett.FORSØK_OPPLÆRING_LENGRE_VARIGHET
                "GRUPPE_AMO" -> TiltakstypeSomGirRett.GRUPPE_AMO
                "GRUPPE_VGS_OG_HØYERE_YRKESFAG" -> TiltakstypeSomGirRett.GRUPPE_VGS_OG_HØYERE_YRKESFAG
                "HØYERE_UTDANNING" -> TiltakstypeSomGirRett.HØYERE_UTDANNING
                "INDIVIDUELL_JOBBSTØTTE" -> TiltakstypeSomGirRett.INDIVIDUELL_JOBBSTØTTE
                "INDIVIDUELL_KARRIERESTØTTE_UNG" -> TiltakstypeSomGirRett.INDIVIDUELL_KARRIERESTØTTE_UNG
                "JOBBKLUBB" -> TiltakstypeSomGirRett.JOBBKLUBB
                "OPPFØLGING" -> TiltakstypeSomGirRett.OPPFØLGING
                "UTVIDET_OPPFØLGING_I_NAV" -> TiltakstypeSomGirRett.UTVIDET_OPPFØLGING_I_NAV
                "UTVIDET_OPPFØLGING_I_OPPLÆRING" -> TiltakstypeSomGirRett.UTVIDET_OPPFØLGING_I_OPPLÆRING
                else -> throw RuntimeException("Verdien '$value' matchet ikke med noen av tiltakstypene som gir rett til tiltakspenger")
            }
        }
    }
}
