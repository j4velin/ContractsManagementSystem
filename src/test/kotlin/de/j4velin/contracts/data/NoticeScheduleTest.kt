package de.j4velin.contracts.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration
import java.time.LocalDate
import java.time.Period

class NoticeScheduleTest {

    private val testStartDate = LocalDate.of(2000, 1, 1)
    private val today = LocalDate.of(2021, 8, 22)

    @Test
    fun `test nextPossibleEnd with notice period`() {
        val noticePeriod = Period.ofDays(3)
        val schedule = NoticeSchedule(startDate = testStartDate, noticePeriod = noticePeriod)
        val possibleEnd = schedule.nextPossibleEnd(today)
        assertEquals(today.plus(noticePeriod), possibleEnd)
    }

    @Test
    fun `test nextPossibleEnd with min duration`() {
        val minDuration = Period.ofYears(1)
        val schedule = NoticeSchedule(startDate = testStartDate, minDuration = minDuration)
        val possibleEnd = schedule.nextPossibleEnd(today)
        // contract started on the 1st January, has to end on 1st January
        assertEquals(LocalDate.of(2022, 1, 1), possibleEnd)
    }

    @Test
    fun `test nextPossibleEnd with notice period and min duration, long notice period`() {
        val noticePeriod = Period.ofMonths(12)
        val minDuration = Period.ofYears(1)
        val schedule = NoticeSchedule(startDate = testStartDate, noticePeriod = noticePeriod, minDuration = minDuration)
        val possibleEnd = schedule.nextPossibleEnd(today)

        // contract started on the 1st January, has to end on 1st January
        assertEquals(1, possibleEnd!!.dayOfYear)
        // already too late for next year due to long notice period
        assertEquals(2023, possibleEnd.year)
    }

    @Test
    fun `test nextPossibleEnd with notice period and min duration, short notice period`() {
        val noticePeriod = Period.ofDays(2)
        val minDuration = Period.ofYears(1)
        val schedule = NoticeSchedule(startDate = testStartDate, noticePeriod = noticePeriod, minDuration = minDuration)
        val possibleEnd = schedule.nextPossibleEnd(today)

        // contract started on the 1st January, has to end on 1st January
        assertEquals(1, possibleEnd!!.dayOfYear)
        // not too late for 2022
        assertEquals(2022, possibleEnd.year)
    }

    @Test
    fun `test nextPossibleEnd with notice period and min duration, min duration shorter than notice period`() {
        val noticePeriod = Period.ofMonths(2)
        val minDuration = Period.ofMonths(1)
        val schedule = NoticeSchedule(startDate = testStartDate, noticePeriod = noticePeriod, minDuration = minDuration)
        val possibleEnd = schedule.nextPossibleEnd(today)

        // contract started on the 1st -> must end on the 1st!
        assertEquals(LocalDate.of(2021, 11, 1), possibleEnd)
    }

    @Test
    fun `test nextPossibleEnd with neither notice period nor min duration`() {
        val schedule = NoticeSchedule(startDate = testStartDate)
        val possibleEnd = schedule.nextPossibleEnd(today)

        // can not get any useful information about possible end dates
        assertNull(possibleEnd)
    }

    @Test
    fun `test latest cancel date to cancel contract with min duration`() {
        val noticePeriod = Period.ofMonths(3)
        val minDuration = Period.ofYears(2)
        val schedule = NoticeSchedule(startDate = testStartDate, noticePeriod = noticePeriod, minDuration = minDuration)
        val cancelDate = schedule.latestCancelDate(today)

        // 3 month before min duration end date
        assertEquals(LocalDate.of(2021, 10, 1), cancelDate)
    }

    @Test
    fun `test latest cancel date to cancel contract without min duration`() {
        val noticePeriod = Period.ofMonths(1)
        val schedule = NoticeSchedule(startDate = testStartDate, noticePeriod = noticePeriod)
        val cancelDate = schedule.latestCancelDate(today)

        // no min duration -> can be canceled today
        assertEquals(today, cancelDate)
    }

    @Test
    fun `test latest cancel date to cancel contract in extension period`() {
        val noticePeriod = Period.ofMonths(2)
        val minDuration = Period.ofMonths(3)
        val extension = Period.ofYears(5)
        val schedule = NoticeSchedule(
            startDate = testStartDate, noticePeriod = noticePeriod, minDuration = minDuration, extension = extension
        )
        val cancelDate = schedule.latestCancelDate(today)

        // 3 months min -> 03/01/2020 -> 5 years extension -> 03/01/2025 -> 2 month notice -> 02/01/2025
        assertEquals(LocalDate.of(2025, 2, 1), cancelDate)
    }

    @Test
    fun `test nextPossibleEnd with null extension and expired first term`() {
        val noticePeriod = Period.ofMonths(2)
        val minDuration = Period.ofMonths(1)
        // fromDTO and old DB documents set extendPeriod to null explicitly -> used to throw a NullPointerException
        val schedule = NoticeSchedule(
            startDate = testStartDate, noticePeriod = noticePeriod, minDuration = minDuration, extension = null
        )
        val possibleEnd = schedule.nextPossibleEnd(today)

        // falls back to renewing by the min duration: contract started on the 1st -> must end on the 1st
        assertEquals(LocalDate.of(2021, 11, 1), possibleEnd)
    }

    @Test
    fun `test nextPossibleEnd with null extension and without notice period`() {
        val schedule = NoticeSchedule(startDate = testStartDate, minDuration = Period.ofYears(1), extension = null)
        val possibleEnd = schedule.nextPossibleEnd(today)

        assertEquals(LocalDate.of(2022, 1, 1), possibleEnd)
    }

    @Test
    fun `test nextPossibleEnd with zero extension`() {
        val noticePeriod = Period.ofMonths(1)
        val schedule = NoticeSchedule(
            startDate = testStartDate,
            noticePeriod = noticePeriod,
            minDuration = Period.ofYears(1),
            extension = Period.ofMonths(0)
        )
        val possibleEnd = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            schedule.nextPossibleEnd(today)
        }

        // no renewal and min duration is over -> can end once the notice period elapsed
        assertEquals(today.plus(noticePeriod), possibleEnd)
    }

    @Test
    fun `test nextPossibleEnd with zero extension and without notice period`() {
        val schedule = NoticeSchedule(startDate = testStartDate, minDuration = Period.ofYears(1), extension = Period.ZERO)
        val possibleEnd = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            schedule.nextPossibleEnd(today)
        }

        assertEquals(today, possibleEnd)
    }

    @Test
    fun `test nextPossibleEnd with zero extension and first term not yet over`() {
        val startDate = LocalDate.of(2021, 1, 1)
        val schedule = NoticeSchedule(
            startDate = startDate,
            noticePeriod = Period.ofMonths(1),
            minDuration = Period.ofYears(1),
            extension = Period.ZERO
        )
        val possibleEnd = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            schedule.nextPossibleEnd(today)
        }

        // the min duration still applies
        assertEquals(LocalDate.of(2022, 1, 1), possibleEnd)
    }

    @Test
    fun `test nextPossibleEnd with negative extension`() {
        val noticePeriod = Period.ofMonths(1)
        val schedule = NoticeSchedule(
            startDate = testStartDate,
            noticePeriod = noticePeriod,
            minDuration = Period.ofYears(1),
            extension = Period.ofMonths(-1)
        )
        val possibleEnd = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            schedule.nextPossibleEnd(today)
        }

        assertEquals(today.plus(noticePeriod), possibleEnd)
    }

    @Test
    fun `test nextPossibleEnd with mixed extension that moves backwards`() {
        val noticePeriod = Period.ofMonths(1)
        // positive months but negative days: +1 month -40 days is always before the current date
        val schedule = NoticeSchedule(
            startDate = testStartDate,
            noticePeriod = noticePeriod,
            minDuration = Period.ofYears(1),
            extension = Period.of(0, 1, -40)
        )
        val possibleEnd = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            schedule.nextPossibleEnd(today)
        }

        assertEquals(today.plus(noticePeriod), possibleEnd)
    }

    @Test
    fun `test nextPossibleEnd keeps the end of the month`() {
        val startDate = LocalDate.of(2024, 1, 31)
        val schedule =
            NoticeSchedule(startDate = startDate, minDuration = Period.ofMonths(1), extension = Period.ofMonths(1))

        fun termEndAfter(date: LocalDate) = schedule.nextPossibleEnd(date.plusDays(1))

        assertEquals(LocalDate.of(2024, 2, 29), termEndAfter(LocalDate.of(2024, 1, 31)))
        assertEquals(LocalDate.of(2024, 3, 31), termEndAfter(LocalDate.of(2024, 2, 29)))
        // used to be Apr 29 and May 29, as each term end was calculated from the previous one
        assertEquals(LocalDate.of(2024, 4, 30), termEndAfter(LocalDate.of(2024, 3, 31)))
        assertEquals(LocalDate.of(2024, 5, 31), termEndAfter(LocalDate.of(2024, 4, 30)))
        assertEquals(LocalDate.of(2025, 2, 28), termEndAfter(LocalDate.of(2025, 1, 31)))
        assertEquals(LocalDate.of(2025, 3, 31), termEndAfter(LocalDate.of(2025, 2, 28)))
    }

    @Test
    fun `test latest cancel date keeps the end of the month`() {
        val startDate = LocalDate.of(2024, 1, 31)
        val schedule = NoticeSchedule(
            startDate = startDate,
            minDuration = Period.ofYears(1),
            noticePeriod = Period.ofMonths(1),
            extension = Period.ofMonths(1)
        )
        // first term ends 2025-01-31, then monthly: 2025-02-28, 2025-03-31, 2025-04-30, 2025-05-31, ...
        val march = LocalDate.of(2025, 3, 30)
        assertEquals(LocalDate.of(2025, 4, 30), schedule.nextPossibleEnd(march))
        assertEquals(LocalDate.of(2025, 3, 30), schedule.latestCancelDate(march))
        val april = LocalDate.of(2025, 4, 1)
        assertEquals(LocalDate.of(2025, 5, 31), schedule.nextPossibleEnd(april))
        assertEquals(LocalDate.of(2025, 4, 30), schedule.latestCancelDate(april))
    }

    @Test
    fun `test nextPossibleEnd with yearly terms starting on a leap day`() {
        val startDate = LocalDate.of(2024, 2, 29)
        val schedule = NoticeSchedule(startDate = startDate, minDuration = Period.ofYears(1))

        assertEquals(LocalDate.of(2025, 2, 28), schedule.nextPossibleEnd(LocalDate.of(2024, 6, 1)))
        assertEquals(LocalDate.of(2027, 2, 28), schedule.nextPossibleEnd(LocalDate.of(2026, 10, 13)))
        // used to be 2028-02-28, as each term end was calculated from the previous one
        assertEquals(LocalDate.of(2028, 2, 29), schedule.nextPossibleEnd(LocalDate.of(2027, 6, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), schedule.nextPossibleEnd(LocalDate.of(2028, 2, 29)))
        assertEquals(LocalDate.of(2029, 2, 28), schedule.nextPossibleEnd(LocalDate.of(2028, 3, 1)))
    }

    @Test
    fun `test nextPossibleEnd with min duration in days and monthly extension`() {
        val startDate = LocalDate.of(2024, 1, 25)
        val schedule =
            NoticeSchedule(startDate = startDate, minDuration = Period.ofDays(10), extension = Period.ofMonths(1))

        // first term ends on Feb 4th, then renews monthly from there
        assertEquals(LocalDate.of(2024, 2, 4), schedule.nextPossibleEnd(LocalDate.of(2024, 2, 1)))
        assertEquals(LocalDate.of(2024, 4, 4), schedule.nextPossibleEnd(LocalDate.of(2024, 3, 10)))
    }

    @Test
    fun `test nextPossibleEnd matches stepping through all terms`() {
        val periods = listOf(
            Period.ofDays(1), Period.ofDays(10), Period.ofWeeks(1), Period.ofWeeks(6), Period.ofMonths(1),
            Period.ofMonths(3), Period.ofMonths(13), Period.ofYears(1), Period.ofYears(4), Period.of(1, 2, 3)
        )
        val starts = generateSequence(LocalDate.of(2019, 12, 28)) { it.plusDays(11) }.takeWhile { it.year < 2022 }
        val nows = listOf(LocalDate.of(2021, 8, 22), LocalDate.of(2024, 2, 29), LocalDate.of(2031, 1, 31))
        for (start in starts) {
            for (min in periods) {
                for (extension in periods) {
                    for (now in nows) {
                        val schedule = NoticeSchedule(startDate = start, minDuration = min, extension = extension)
                        assertEquals(
                            naiveNextPossibleEnd(start, min, extension, now),
                            schedule.nextPossibleEnd(now),
                            "start=$start min=$min extension=$extension now=$now"
                        )
                    }
                }
            }
        }
    }

    /**
     * Reference implementation: steps through the terms one by one, each calculated from the start of the contract
     */
    private fun naiveNextPossibleEnd(start: LocalDate, min: Period, extension: Period, now: LocalDate): LocalDate {
        var n = 0
        while (true) {
            val termEnd = if (min.days == 0) {
                start.plus(min.plus(extension.multipliedBy(n)))
            } else {
                start.plus(min).plus(extension.multipliedBy(n))
            }
            if (!termEnd.isBefore(now)) {
                return termEnd
            }
            n++
        }
    }

    @Test
    fun `test nextPossibleEnd is fast for long running contracts with short terms`() {
        val startDate = LocalDate.of(1000, 1, 1)
        val now = LocalDate.of(2026, 10, 13)
        val daily = NoticeSchedule(startDate = startDate, minDuration = Period.ofDays(1), extension = Period.ofDays(1))
        val dailyWithNotice = NoticeSchedule(
            startDate = startDate,
            minDuration = Period.ofDays(1),
            extension = Period.ofDays(1),
            noticePeriod = Period.ofWeeks(1)
        )
        val monthly =
            NoticeSchedule(startDate = startDate, minDuration = Period.ofMonths(1), extension = Period.ofMonths(1))

        // used to take ~375,000 iterations per call. Called many times, as the list view sorts and shows all contracts.
        // A coarse limit, so a busy CI runner can't fail it; the old loop took about 9 seconds for this.
        val results = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            (1..1000).map {
                listOf(
                    daily.nextPossibleEnd(now),
                    dailyWithNotice.nextPossibleEnd(now),
                    dailyWithNotice.latestCancelDate(now),
                    monthly.nextPossibleEnd(now)
                )
            }.distinct()
        }

        assertEquals(
            listOf(listOf(now, LocalDate.of(2026, 10, 20), now, LocalDate.of(2026, 11, 1))),
            results
        )
    }

    @Test
    fun `cancel-by date is the end date minus the notice period`() {
        // contract ends on Feb 28, 2027 (yearly term): notice must be given by Jan 28, even though Jan 31 + 1 month
        // would also be Feb 28 - deadlines are read cautiously
        val start = LocalDate.of(2026, 2, 28)
        val schedule =
            NoticeSchedule(startDate = start, minDuration = Period.ofYears(1), noticePeriod = Period.ofMonths(1))
        val now = LocalDate.of(2027, 1, 10)
        assertEquals(LocalDate.of(2027, 2, 28), schedule.nextPossibleEnd(now))
        assertEquals(LocalDate.of(2027, 1, 28), schedule.latestCancelDate(now))
    }

    @Test
    fun `term end is missed once its cancel-by date has passed`() {
        val start = LocalDate.of(2026, 2, 28)
        val schedule =
            NoticeSchedule(startDate = start, minDuration = Period.ofYears(1), noticePeriod = Period.ofMonths(1))
        // Jan 28 was the last day for Feb 28, 2027 -> on Jan 29 the next term (Feb 28, 2028) is the earliest end
        val now = LocalDate.of(2027, 1, 29)
        assertEquals(LocalDate.of(2028, 2, 28), schedule.nextPossibleEnd(now))
        assertEquals(LocalDate.of(2028, 1, 28), schedule.latestCancelDate(now))
    }

    @Test
    fun `cancel-by date is never yesterday at the end of a month`() {
        val schedule = NoticeSchedule(noticePeriod = Period.ofMonths(1))
        // Mar 31 + 1 month is Apr 30, but Apr 30 - 1 month is Mar 30 (yesterday): Apr 30 can't be reached anymore,
        // the earliest end is May 1, to be canceled by Apr 1
        val now = LocalDate.of(2026, 3, 31)
        assertEquals(LocalDate.of(2026, 5, 1), schedule.nextPossibleEnd(now))
        assertEquals(LocalDate.of(2026, 4, 1), schedule.latestCancelDate(now))
        // one day earlier, Apr 30 can still be reached by canceling today
        val dayBefore = LocalDate.of(2026, 3, 30)
        assertEquals(LocalDate.of(2026, 4, 30), schedule.nextPossibleEnd(dayBefore))
        assertEquals(dayBefore, schedule.latestCancelDate(dayBefore))
    }

    @Test
    fun `monthly term ending Apr 30 is missed on Mar 31`() {
        // monthly terms from Jan 31: Apr 30 needs notice by Mar 30, so on Mar 31 the next term (May 31) is the earliest
        val start = LocalDate.of(2024, 1, 31)
        val schedule = NoticeSchedule(
            startDate = start,
            minDuration = Period.ofMonths(1),
            extension = Period.ofMonths(1),
            noticePeriod = Period.ofMonths(1)
        )
        assertEquals(LocalDate.of(2026, 3, 30), schedule.latestCancelDate(LocalDate.of(2026, 3, 15)))
        val now = LocalDate.of(2026, 3, 31)
        assertEquals(LocalDate.of(2026, 5, 31), schedule.nextPossibleEnd(now))
        assertEquals(LocalDate.of(2026, 4, 30), schedule.latestCancelDate(now))
    }

    @Test
    fun `cancel-by date is never in the past and leaves the full notice period`() {
        val notices = listOf(
            Period.ofDays(10), Period.ofWeeks(2), Period.ofMonths(1), Period.ofMonths(2), Period.ofMonths(3),
            Period.ofYears(1)
        )
        val starts = listOf(null, LocalDate.of(2023, 1, 31), LocalDate.of(2023, 8, 30), LocalDate.of(2020, 2, 29))
        var day = LocalDate.of(2024, 1, 1)
        while (day.year < 2028) {
            for (notice in notices) {
                for (start in starts) {
                    val schedule = if (start == null) NoticeSchedule(noticePeriod = notice)
                    else NoticeSchedule(startDate = start, minDuration = Period.ofMonths(1), noticePeriod = notice)
                    val end = schedule.nextPossibleEnd(day)!!
                    val cancelBy = schedule.latestCancelDate(day)!!
                    val case = "today=$day notice=$notice start=$start end=$end cancelBy=$cancelBy"
                    assertFalse(cancelBy.isBefore(day), "cancel-by date in the past: $case")
                    assertEquals(end.minus(notice), cancelBy, "cancel-by date doesn't leave the full notice period: $case")
                    if (start == null) {
                        // no terms: the contract can end on any day, so one day earlier must not be reachable anymore
                        assertTrue(end.minusDays(1).minus(notice).isBefore(day), "a day earlier would be possible: $case")
                    }
                }
            }
            day = day.plusDays(1)
        }
    }
}
