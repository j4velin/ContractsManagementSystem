package de.j4velin.contracts.data

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

/**
 * The notice and term dates of a contract, which is not canceled yet: the earliest date at which it can end, and the
 * latest date to cancel it for that.
 *
 * Pure date arithmetic: it never reads the current date, every function takes "today" as a parameter.
 *
 * Deadlines are read cautiously:
 * - the cancel-by date of an end date is the end date minus the notice period
 * - an end date, whose cancel-by date has passed, is missed: the contract can only end at a later date
 *
 * @property startDate the contract's start date, if known
 * @property minDuration the minimum duration, the contract must be active, if known
 * @property extension the period the contract is extended after the minimum duration, if not canceled in time. If
 * null (e.g. the user left the extension empty, or the stored document has no value), the contract is assumed to renew
 * by [minDuration], the same as the default of [Cancellation.extendPeriod]
 * @property noticePeriod the period of notice the contract requires to cancel, if known
 */
internal data class NoticeSchedule(
    val startDate: LocalDate? = null,
    val minDuration: Period? = null,
    val extension: Period? = null,
    val noticePeriod: Period? = null
) {
    private companion object {
        /**
         * Average length of a month in the Gregorian calendar (146097 days per 400 years = 4800 months)
         */
        const val AVERAGE_DAYS_PER_MONTH = 146097.0 / 4800
    }

    /**
     * Calculates the next date at which the contract can end, if it is canceled today.
     *
     * If [extension] is null, the contract is assumed to renew by [minDuration].
     *
     * If the extension period can not move the term end forward (zero, negative or mixed periods with a negative part
     * like "P1M-40D"), the contract is treated as having no automatic renewal: once the minimum duration has passed, it
     * can end as soon as the notice period allows.
     *
     * The term ends are always calculated from the contract's start instead of adding the extension to the previous
     * term end, so they don't drift at the end of a month: starting on Jan 31 with a minimum duration of one month and
     * a monthly extension, the terms end on Feb 29, Mar 31, Apr 30, ... instead of Feb 29, Mar 29, Apr 29, ...
     *
     * @param today the current date
     * @return the next possible end date for the contract or null, if neither a notice period nor a start date with a
     * minimum duration is known
     */
    fun nextPossibleEnd(today: LocalDate): LocalDate? {
        if (noticePeriod == null && (startDate == null || minDuration == null)) {
            return null
        }
        var possibleEnd = today
        if (noticePeriod != null) {
            possibleEnd = earliestEndWithNotice(noticePeriod, today)
        }
        if (startDate != null && minDuration != null) {
            possibleEnd = firstTermEndNotBefore(startDate, minDuration, extension ?: minDuration, possibleEnd)
        }
        return possibleEnd
    }

    /**
     * Calculates the latest date to cancel the contract, so that it ends at [nextPossibleEnd].
     *
     * This is the cautious reading of the deadline: the full notice period before the end date. [nextPossibleEnd]
     * only returns end dates, for which this isn't in the past.
     *
     * @param today the current date
     * @return the latest possible date to cancel the contract as soon as possible, or null, if no notice period is set
     */
    fun latestCancelDate(today: LocalDate): LocalDate? {
        if (noticePeriod == null) {
            return null
        }
        return nextPossibleEnd(today)?.minus(noticePeriod)
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
     * @param today the current date
     * @return the earliest reachable end date
     */
    private fun earliestEndWithNotice(notice: Period, today: LocalDate): LocalDate {
        // today plus the notice period is never too early; month lengths differ by at most 3 days, so at most 3 steps
        var end = today.plus(notice)
        while (end.minus(notice).isBefore(today)) {
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
    private fun firstTermEndNotBefore(
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
}
