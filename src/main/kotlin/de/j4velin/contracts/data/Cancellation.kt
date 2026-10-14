package de.j4velin.contracts.data

import de.j4velin.contracts.parseDate
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.Period

/**
 * Data class representing the cancellation data of a [Contract]
 *
 * The notice and term dates are calculated by a [NoticeSchedule], see [noticeSchedule]
 *
 * @property minDuration the minimum duration, the contract must be active
 * @property noticePeriod the period of notice the contract requires to cancel
 * @property extendPeriod the period the contract is extended after the initial duration, if not canceled in time
 * @property endDate the date at which the contract ends
 * @property canceled true, if the contract is canceled
 * @property ack true, if the cancellation has been acknowledged by the other party
 */
data class Cancellation(
    val minDuration: Period? = null,
    val noticePeriod: Period? = null,
    val extendPeriod: Period? = minDuration,
    val endDate: LocalDate? = null,
    val canceled: Boolean = endDate != null,
    val ack: Boolean = false
) {
    companion object {
        /**
         * Upper limit for the number of units of a period. Keeps the date calculations within the range of [LocalDate]
         * (larger values used to crash the contract list) and matches the 3-digit input field
         */
        internal const val MAX_PERIOD_AMOUNT = 999

        /**
         * Creates a [Cancellation] from a [ContractDTO]
         * @param data the DTO data
         * @return the corresponding [Cancellation] object
         */
        fun fromDTO(data: ContractDTO) = Cancellation(
            minDuration = parsePeriod(data.minDurationValue, data.minDurationUnit, "minDuration"),
            noticePeriod = parsePeriod(data.noticePeriodValue, data.noticePeriodUnit, "noticePeriod"),
            extendPeriod = parsePeriod(data.extendPeriodValue, data.extendPeriodUnit, "extendPeriod"),
            endDate = parseDate(data.endDate, "endDate"),
            canceled = data.canceled != null || data.endDate?.isBlank() == false,
            ack = data.canceled != null && data.cancelAck != null
        )

        /**
         * Parses a period from the separate value and unit form fields.
         *
         * @param value the number of units. Null or blank means "no period"
         * @param unit the unit: D (days), W (weeks), M (months) or Y (years). Null means "no period"
         * @param field the name of the form field, used in the error message
         * @return the parsed [Period] or null, if either the value or the unit is not set
         * @throws ResponseStatusException with [HttpStatus.BAD_REQUEST], if the value is not an integer between 0 and
         * [MAX_PERIOD_AMOUNT] or the unit is unknown
         */
        private fun parsePeriod(value: String?, unit: Char?, field: String): Period? {
            if (unit == null || value.isNullOrBlank()) {
                return null
            }
            val amount = value.trim().takeIf { it.all { c -> c in '0'..'9' } }?.toIntOrNull()
                ?.takeIf { it <= MAX_PERIOD_AMOUNT }
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid $field value '$value': must be an integer between 0 and $MAX_PERIOD_AMOUNT"
                )
            return when (unit) {
                'D' -> Period.ofDays(amount)
                'W' -> Period.ofWeeks(amount)
                'M' -> Period.ofMonths(amount)
                'Y' -> Period.ofYears(amount)
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid $field unit '$unit'")
            }
        }
    }

    /**
     * @return true, if any cancellation information is set
     */
    fun hasCancellationInfo() =
        minDuration != null || noticePeriod != null || extendPeriod != null || canceled || endDate != null

    /**
     * Toggles the [canceled] state. Might also affect the [endDate] and [ack] properties
     *
     * @param startDate the contract's start date, if known
     * @param today the current date
     * @param newAck the new cancel acknowledged value. Only considered, if this operation sets the state to 'canceled'
     * @return the modified copy of this cancellation
     */
    internal fun toggleCancel(startDate: LocalDate?, today: LocalDate, newAck: Boolean = false): Cancellation =
        if (canceled) {
            // now un-canceling -> also clear the end date: a set end date implies 'canceled' (see fromDTO), and the
            // next possible end date has to be calculated again instead of staying frozen at the old value
            copy(canceled = false, endDate = null, ack = false)
        } else {
            // now canceling -> set end date the same as the next possible end date, if not set yet
            copy(canceled = true, endDate = endDate ?: getNextPossibleEndDate(startDate, today), ack = newAck)
        }

    /**
     * Toggles the [ack] state. Might also affect the [endDate] and [canceled] properties
     *
     * @param startDate the contract's start date, if known
     * @param today the current date
     * @return the modified copy of this cancellation
     */
    internal fun toggleCancelAck(startDate: LocalDate?, today: LocalDate): Cancellation =
        if (!canceled && !ack) {
            // not canceled and not acknowledged? -> toggle both
            toggleCancel(startDate, today, newAck = true)
        } else {
            copy(ack = !ack)
        }

    /**
     * @param startDate the contract's start date, if known
     * @return the notice and term schedule of this cancellation's periods for a contract started at [startDate]. It
     * ignores [endDate], see [getNextPossibleEndDate] and [getLatestPossibleCancelDate]
     */
    internal fun noticeSchedule(startDate: LocalDate?) = NoticeSchedule(
        startDate = startDate,
        minDuration = minDuration,
        extension = extendPeriod,
        noticePeriod = noticePeriod
    )

    /**
     * @param startDate the contract's start date, if known
     * @param today the current date
     * @return the [endDate], if set. Otherwise, the next date at which the contract can end, if it is canceled today
     * (see [NoticeSchedule.nextPossibleEnd]) or null, if that information is not available
     */
    internal fun getNextPossibleEndDate(startDate: LocalDate?, today: LocalDate): LocalDate? =
        endDate ?: noticeSchedule(startDate).nextPossibleEnd(today)

    /**
     * @param startDate the contract's start date, if known
     * @param today the current date
     * @return the latest possible date to cancel the contract as soon as possible (see
     * [NoticeSchedule.latestCancelDate]), or null, if no notice period is set or if the contract already has an
     * [endDate]
     */
    internal fun getLatestPossibleCancelDate(startDate: LocalDate?, today: LocalDate): LocalDate? =
        if (endDate != null) null else noticeSchedule(startDate).latestCancelDate(today)
}
