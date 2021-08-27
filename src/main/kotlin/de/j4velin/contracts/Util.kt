package de.j4velin.contracts

import org.springframework.context.i18n.LocaleContextHolder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Extension function to print the date in medium style with the users locale
 */
fun LocalDate.localized() = this.format(
    DateTimeFormatter.ofLocalizedDate(
        FormatStyle.MEDIUM
    ).withLocale(LocaleContextHolder.getLocale())
)