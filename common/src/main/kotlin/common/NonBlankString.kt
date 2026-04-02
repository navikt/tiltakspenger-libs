package no.nav.tiltakspenger.libs.common

import arrow.core.Either
import arrow.core.left
import arrow.core.right

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
         * @return [Either.Left] with error message if string is blank, [Either.Right] with [NonBlankString] otherwise.
         */
        fun fromString(string: String): Either<String, NonBlankString> =
            if (string.isBlank()) {
                "String cannot be blank".left()
            } else {
                NonBlankString(string).right()
            }

        /**
         * throws [IllegalArgumentException] if string is blank
         */
        fun String.toNonBlankString(): NonBlankString = create(this)
    }
}
