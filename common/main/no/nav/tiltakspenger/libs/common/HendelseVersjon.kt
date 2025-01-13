package no.nav.tiltakspenger.libs.common

@JvmInline
value class HendelseVersjon(val value: Int) : Comparable<HendelseVersjon> {

    init {
        require(value > 0L) { "Versjonen må være større enn 0L" }
    }

    override fun compareTo(other: HendelseVersjon): Int {
        return this.value.compareTo(other.value)
    }

    operator fun inc() = HendelseVersjon(this.value + 1)
    operator fun inc(value: Int) = HendelseVersjon(this.value + value)

    override fun toString() = value.toString()

    companion object {
        /**
         * [ny] er ment å brukes direkte.
         * Det vil si at man skal opprette en ny versjon, for å så gjøre en [inc] på den nye hendelsen
         */
        fun ny(): HendelseVersjon = HendelseVersjon(1)

        fun max(first: HendelseVersjon, second: HendelseVersjon): HendelseVersjon =
            if (first > second) first else second

        fun max(first: HendelseVersjon?, second: HendelseVersjon): HendelseVersjon = if (first == null) second else max(first, second)

        fun max(first: HendelseVersjon, second: HendelseVersjon?): HendelseVersjon = if (second == null) first else max(first, second)
    }
}
