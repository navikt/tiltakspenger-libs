package no.nav.tiltakspenger.libs.common

@JvmInline
value class NonBlankString private constructor(
    val value: String,
) {
    companion object {
        /**
         * throws [IllegalArgumentException] if string is blank
         */
        fun create(string: String): NonBlankString =
            if (string.isBlank()) {
                throw IllegalArgumentException("String cannot be blank")
            } else {
                NonBlankString(string)
            }

        /**
         * throws [IllegalArgumentException] if string is blank
         */
        fun String.toNonBlankString(): NonBlankString = create(this)
    }
}
