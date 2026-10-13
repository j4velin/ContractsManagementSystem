package de.j4velin.contracts

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/**
 * Extension function to print the date in medium style with the users locale
 */
fun LocalDate.localized() = this.format(
    DateTimeFormatter.ofLocalizedDate(
        FormatStyle.MEDIUM
    ).withLocale(LocaleContextHolder.getLocale())
)

/**
 * Parses an ISO-8601 date (yyyy-MM-dd) from a form field
 *
 * @param value the raw form value. Null or blank means "no date"
 * @param field the name of the form field, used in the error message
 * @return the parsed date or null, if no value is given
 * @throws ResponseStatusException with [HttpStatus.BAD_REQUEST], if the value is not a valid date with a four-digit
 * year
 */
internal fun parseDate(value: String?, field: String): LocalDate? {
    if (value.isNullOrBlank()) {
        return null
    }
    val date = try {
        LocalDate.parse(value.trim())
    } catch (e: DateTimeParseException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid $field '$value'", e)
    }
    // ISO dates may have years like +999999999, for which adding periods overflows LocalDate
    if (date.year !in 1000..9999) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid $field '$value': year out of range")
    }
    return date
}
