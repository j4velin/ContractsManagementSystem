package de.j4velin.contracts.data

import de.j4velin.contracts.parseDate
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

/**
 * Data class representing the cancellation data of a [Contract]
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

        /**
         * Upper limit for the number of units of a period. Keeps the date calculations within the range of [LocalDate]
         * (larger values used to crash the contract list) and matches the 3-digit input field
         */
        internal const val MAX_PERIOD_AMOUNT = 999

        /**
         * Average length of a month in the Gregorian calendar (146097 days per 400 years = 4800 months)
         */
        private const val AVERAGE_DAYS_PER_MONTH = 146097.0 / 4800

        /**
         * Toggles the [Cancellation.canceled] state. Might also affect [Cancellation.endDate] and [Cancellation.ack]
         * properties
         *
         * @param contract the contract on which to toggle the cancel state
         * @param newAck the new cancel acknowledged value. Only considered, if this operation sets the state to 'canceled'
         * @return the modified [Contract]
         */
        fun toggleCancel(contract: Contract, newAck: Boolean = false): Contract {
            val cancellation = contract.cancellation
            val newCancellation = if (cancellation.canceled) {
                // now un-canceling -> also clear the end date: a set end date implies 'canceled' (see fromDTO), and the
                // next possible end date has to be calculated again instead of staying frozen at the old value
                cancellation.copy(canceled = false, endDate = null, ack = false)
            } else {
                // now canceling -> set end date the same as the next possible end date, if not set yet
                val endDate = cancellation.endDate
                    ?: cancellation.getNextPossibleEndDate(contract.startDate, LocalDate.now())
                cancellation.copy(canceled = true, endDate = endDate, ack = newAck)
            }
            return contract.copy(cancellation = newCancellation)
        }

        /**
         * Toggles the [Cancellation.ack] state. Might also affect [Cancellation.endDate] and [Cancellation.canceled]
         * properties
         *
         * @param contract the contract on which to toggle the cancel ack state
         * @return the modified [Contract]
         */
        fun toggleCancelAck(contract: Contract): Contract {
            return if (!contract.cancellation.canceled && !contract.cancellation.ack) {
                // not canceled and not acknowledged? -> toggle both
                toggleCancel(contract, true)
            } else {
                contract.copy(cancellation = contract.cancellation.copy(ack = !contract.cancellation.ack))
            }
        }
    }

    /**
     * @return true, if any cancellation information is set
     */
    fun hasCancellationInfo() =
        minDuration != null || noticePeriod != null || extendPeriod != null || canceled || endDate != null

    /**
     * Calculates the next date at which the contract can end, if it is canceled now.
     *
     * If [extendPeriod] is null (e.g. the user left the extension empty, or the stored document has no value), the
     * contract is assumed to renew by [minDuration], the same as the constructor default.
     *
     * If the extension period can not move the term end forward (zero, negative or mixed periods with a negative part
     * like "P1M-40D"), the contract is treated as having no automatic renewal: once the minimum duration has passed, it
     * can end as soon as the notice period allows.
     *
     * The term ends are always calculated from the contract's start instead of adding the extension to the previous
     * term end, so they don't drift at the end of a month: starting on Jan 31 with a minimum duration of one month and
     * a monthly extension, the terms end on Feb 29, Mar 31, Apr 30, ... instead of Feb 29, Mar 29, Apr 29, ...
     *
     * @param startDate the contract's start date, if known
     * @param now LocalDate.now() (parameterized for testing)
     * @return the next possible end date for the contract or null, if that information is not available
     */
    internal fun getNextPossibleEndDate(startDate: LocalDate?, now: LocalDate = LocalDate.now()): LocalDate? {
        if (endDate != null) {
            return endDate
        }
        if (noticePeriod == null && (startDate == null || minDuration == null)) {
            return null
        }
        var possibleEnd = now
        if (noticePeriod != null) {
            possibleEnd = getEarliestEndWithNotice(noticePeriod, now)
        }
        if (startDate != null && minDuration != null) {
            possibleEnd = getFirstTermEndNotBefore(startDate, minDuration, extendPeriod ?: minDuration, possibleEnd)
        }
        return possibleEnd
    }

    /**
     * Finds the earliest date at which the contract can end, if it is canceled today with the given notice period.
     *
     * Deadlines are read cautiously: an end date can only be reached if "end date minus notice period" is not before
     * today, which is also the cancel-by date shown for it. Subtracting months isn't the inverse of adding them, so
     * this can be later than today plus the notice period: on Mar 31 with one month notice, Apr 30 can't be reached
     * (Apr 30 minus one month is Mar 30, yesterday), so the earliest end is May 1, to be canceled by Apr 1.
     *
     * Every later date can be reached as well, as "date minus notice period" never decreases for a later date.
     *
     * @param notice the notice period
     * @param now LocalDate.now()
     * @return the earliest reachable end date
     */
    private fun getEarliestEndWithNotice(notice: Period, now: LocalDate): LocalDate {
        // today plus the notice period is never too early; month lengths differ by at most 3 days, so at most 3 steps
        var end = now.plus(notice)
        while (end.minus(notice).isBefore(now)) {
            end = end.plusDays(1)
        }
        return end
    }

    /**
     * Finds the first end of a contract term, which is not before the given target date.
     *
     * The first term ends after [minDuration], the n-th renewal ends n times [extension] later. Instead of stepping
     * through all the terms, n is estimated from the average length of the extension and then corrected by at most a
     * few steps, so this stays fast even for a contract started centuries ago, which renews daily.
     *
     * @param startDate the contract's start date
     * @param minDuration the minimum duration of the contract
     * @param extension the period by which the contract is extended, if not canceled in time
     * @param target the earliest date at which the contract may end
     * @return the first term end, which is not before [target], or [target] itself, if the first term is over and the
     * extension can not move the term end forward
     */
    private fun getFirstTermEndNotBefore(
        startDate: LocalDate,
        minDuration: Period,
        extension: Period,
        target: LocalDate
    ): LocalDate {
        val firstTermEnd = startDate.plus(minDuration)
        if (!firstTermEnd.isBefore(target)) {
            return firstTermEnd
        }
        if (extension.isZero || extension.isNegative) {
            // The extension can not move the term end forward (Period.isNegative is true if any part is negative).
            // Treat the contract as not renewing: the minimum duration is over, so it can end once the notice period
            // elapsed.
            return target
        }
        // LocalDate.plus(Period) adds the months first, then the days. If the minimum duration has no days part, count
        // all months from the start date, so that the end of a month is kept (Jan 31 + 1 + n months is always the last
        // day of a month). Otherwise, count the extensions from the end of the first term.
        val anchor: LocalDate
        val anchorMonths: Long
        if (minDuration.days == 0) {
            anchor = startDate
            anchorMonths = minDuration.toTotalMonths()
        } else {
            anchor = firstTermEnd
            anchorMonths = 0
        }
        val extensionMonths = extension.toTotalMonths()
        val extensionDays = extension.days.toLong()

        // strictly increasing in n, as no part of the extension is negative and at least one is positive
        fun termEnd(n: Long): LocalDate =
            anchor.plusMonths(anchorMonths + n * extensionMonths).plusDays(n * extensionDays)

        // the actual length of n months differs from n average months by only a few days, so the estimate is off by
        // at most a step or two
        val averageExtensionDays = extensionMonths * AVERAGE_DAYS_PER_MONTH + extensionDays
        var n = max(1L, ceil(ChronoUnit.DAYS.between(firstTermEnd, target) / averageExtensionDays).toLong())
        while (n > 1 && !termEnd(n - 1).isBefore(target)) {
            n--
        }
        while (termEnd(n).isBefore(target)) {
            n++
        }
        return termEnd(n)
    }

    /**
     * @param startDate the contract's start date, if known
     * @param now LocalDate.now() (parameterized for testing)
     * @return the latest possible date to cancel the contract as soon as possible, or null, if no notice period is set
     * or if the contract is already canceled
     */
    internal fun getLatestPossibleCancelDate(startDate: LocalDate?, now: LocalDate = LocalDate.now()): LocalDate? {
        if (endDate != null || noticePeriod == null) {
            return null
        }
        // cautious reading: the full notice period before the end date. getNextPossibleEndDate only returns end dates,
        // for which this isn't in the past
        return getNextPossibleEndDate(startDate, now)?.minus(noticePeriod)
    }
}