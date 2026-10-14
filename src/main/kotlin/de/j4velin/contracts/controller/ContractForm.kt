package de.j4velin.contracts.controller

import de.j4velin.contracts.data.Bonus
import de.j4velin.contracts.data.Cancellation
import de.j4velin.contracts.data.Contract
import org.springframework.validation.Errors
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeParseException

/**
 * The form-backing object of the contract page (`contract.html`), which adds and edits contracts.
 *
 * It holds the raw form values, so that the page can show them again exactly as entered, next to the errors found in
 * them. [from] fills it with an existing contract for the edit page, [toContract] converts the submitted values into a
 * [Contract] and reports invalid input as field errors.
 *
 * The property names are the names of the form fields. A period is entered in two fields, `<name>Value` (the number
 * of units) and `<name>Unit` (D, W, M or Y).
 */
class ContractForm {
    var type: String = ""
    var company: String = ""
    var notes: String? = null
    var startDate: String? = null
    var minDurationValue: String? = null
    var minDurationUnit: String? = null
    var noticePeriodValue: String? = null
    var noticePeriodUnit: String? = null
    var extendPeriodValue: String? = null
    var extendPeriodUnit: String? = null
    var canceled: Boolean = false
    var cancelAck: Boolean = false
    var endDate: String? = null
    var bonusDescription: String? = null
    var bonusReceived: Boolean = false

    /**
     * @return true, if any of the cancellation fields is filled in, so that the page shows that section expanded.
     * Invalid input is never blank, so this also expands the section whenever one of its fields has an error
     */
    fun hasCancellationInput() = canceled || cancelAck || listOf(
        minDurationValue, minDurationUnit, noticePeriodValue, noticePeriodUnit, extendPeriodValue, extendPeriodUnit,
        endDate
    ).any { !it.isNullOrBlank() }

    /**
     * @return true, if any of the bonus fields is filled in, so that the page shows that section expanded
     */
    fun hasBonusInput() = bonusReceived || !bonusDescription.isNullOrBlank()

    /**
     * Converts the submitted values into a contract. Checks all fields and reports every invalid one in [errors]
     * (with one of the `contract.error.*` message codes) instead of stopping at the first.
     *
     * - type and company are required and stored trimmed
     * - a period needs a value and a unit, otherwise there is no period. The value must be an integer between 0 and
     *   [MAX_PERIOD_AMOUNT], the unit one of D (days), W (weeks), M (months) or Y (years)
     * - dates are ISO-8601 dates (yyyy-MM-dd) with a four-digit year. Blank means no date
     * - a set end date implies canceled, and the cancellation can only be acknowledged, if the canceled box is checked
     * - a blank bonus description means no description
     *
     * @param userId the id of the contract's owner
     * @param id the id of the edited contract, or null for a new one
     * @param errors receives the field errors. May already contain errors, for example from data binding
     * @return the contract, or null, if [errors] has any errors
     */
    fun toContract(userId: String, id: String?, errors: Errors): Contract? {
        val trimmedType = required("type", type, errors)
        val trimmedCompany = required("company", company, errors)
        val parsedStartDate = parseDate("startDate", startDate, errors)
        val cancellation = Cancellation(
            minDuration = parsePeriod("minDuration", minDurationValue, minDurationUnit, errors),
            noticePeriod = parsePeriod("noticePeriod", noticePeriodValue, noticePeriodUnit, errors),
            extendPeriod = parsePeriod("extendPeriod", extendPeriodValue, extendPeriodUnit, errors),
            endDate = parseDate("endDate", endDate, errors),
            canceled = canceled || !endDate.isNullOrBlank(),
            ack = canceled && cancelAck
        )
        val bonus = Bonus(
            description = bonusDescription?.takeIf { it.isNotBlank() },
            received = bonusReceived
        )
        if (errors.hasErrors()) {
            return null
        }
        return Contract(
            userId,
            type = trimmedType,
            company = trimmedCompany,
            notes = notes,
            startDate = parsedStartDate,
            cancellation = cancellation,
            bonus = bonus,
            id = id
        )
    }

    companion object {
        /**
         * Upper limit for the number of units of a period. Keeps the date calculations within the range of [LocalDate]
         * (larger values used to crash the contract list) and matches the 3-digit input field
         */
        internal const val MAX_PERIOD_AMOUNT = 999

        internal const val ERROR_REQUIRED = "contract.error.required"
        internal const val ERROR_PERIOD_AMOUNT = "contract.error.periodAmount"
        internal const val ERROR_PERIOD_UNIT = "contract.error.periodUnit"
        internal const val ERROR_DATE = "contract.error.date"
        internal const val ERROR_DATE_YEAR = "contract.error.dateYear"

        private val PERIOD_UNITS: Map<String, (Int) -> Period> = mapOf(
            "D" to Period::ofDays,
            "W" to Period::ofWeeks,
            "M" to Period::ofMonths,
            "Y" to Period::ofYears
        )

        /**
         * @param contract the contract to edit
         * @return a form showing the values of the given contract
         */
        fun from(contract: Contract) = ContractForm().apply {
            type = contract.type
            company = contract.company
            notes = contract.notes
            startDate = contract.startDate?.toString()
            val cancellation = contract.cancellation
            toFormValues(cancellation.minDuration).let { minDurationValue = it.first; minDurationUnit = it.second }
            toFormValues(cancellation.noticePeriod).let { noticePeriodValue = it.first; noticePeriodUnit = it.second }
            toFormValues(cancellation.extendPeriod).let { extendPeriodValue = it.first; extendPeriodUnit = it.second }
            canceled = cancellation.canceled
            cancelAck = cancellation.ack
            endDate = cancellation.endDate?.toString()
            bonusDescription = contract.bonus.description
            bonusReceived = contract.bonus.received
        }

        /**
         * The form only creates single-unit periods. Weeks are stored as days, so a whole number of weeks is shown in
         * weeks
         *
         * @param period the stored period
         * @return the value and the unit of the period's form fields, both null for no period
         */
        internal fun toFormValues(period: Period?): Pair<String?, String?> = when {
            period == null -> null to null
            period.years != 0 -> period.years.toString() to "Y"
            period.months != 0 -> period.months.toString() to "M"
            period.days != 0 && period.days % 7 == 0 -> (period.days / 7).toString() to "W"
            else -> period.days.toString() to "D"
        }

        /**
         * @return the trimmed value. Rejects the field, if the value is blank
         */
        private fun required(field: String, value: String, errors: Errors): String =
            value.trim().also { if (it.isEmpty()) errors.rejectValue(field, ERROR_REQUIRED) }

        /**
         * Parses a period from its separate value and unit form fields
         *
         * @param field the name of the period. Its fields are `<field>Value` and `<field>Unit`
         * @param value the number of units. Null or blank means "no period"
         * @param unit the unit: D (days), W (weeks), M (months) or Y (years). Null or empty means "no period"
         * @param errors receives an error for each invalid field
         * @return the period, or null if either the value or the unit is not set or invalid
         */
        private fun parsePeriod(field: String, value: String?, unit: String?, errors: Errors): Period? {
            if (unit.isNullOrEmpty() || value.isNullOrBlank()) {
                return null
            }
            val amount = value.trim().takeIf { it.all { c -> c in '0'..'9' } }?.toIntOrNull()
                ?.takeIf { it <= MAX_PERIOD_AMOUNT }
            if (amount == null) {
                errors.rejectValue("${field}Value", ERROR_PERIOD_AMOUNT, arrayOf(MAX_PERIOD_AMOUNT), null)
            }
            val toPeriod = PERIOD_UNITS[unit]
            if (toPeriod == null) {
                errors.rejectValue("${field}Unit", ERROR_PERIOD_UNIT)
            }
            return if (amount != null && toPeriod != null) toPeriod(amount) else null
        }

        /**
         * Parses an ISO-8601 date (yyyy-MM-dd) from a form field
         *
         * @param field the name of the form field
         * @param value the raw form value. Null or blank means "no date"
         * @param errors receives an error, if the value is not a valid date with a four-digit year
         * @return the date, or null if no value is given or it is invalid
         */
        private fun parseDate(field: String, value: String?, errors: Errors): LocalDate? {
            if (value.isNullOrBlank()) {
                return null
            }
            val date = try {
                LocalDate.parse(value.trim())
            } catch (e: DateTimeParseException) {
                errors.rejectValue(field, ERROR_DATE)
                return null
            }
            // ISO dates may have years like +999999999, for which adding periods overflows LocalDate
            if (date.year !in 1000..9999) {
                errors.rejectValue(field, ERROR_DATE_YEAR)
                return null
            }
            return date
        }
    }
}
