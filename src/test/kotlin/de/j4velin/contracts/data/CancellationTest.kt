package de.j4velin.contracts.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Period

class CancellationTest {

    private val testStartDate = LocalDate.of(2000, 1, 1)
    private val today = LocalDate.of(2021, 8, 22)

    @Test
    fun `test getNextPossibleEndDate with notice period`() {
        val noticePeriod = Period.ofDays(3)
        val cancelInfo = Cancellation(noticePeriod = noticePeriod)
        val possibleEnd = cancelInfo.getNextPossibleEndDate(testStartDate, today)
        assertEquals(today.plus(noticePeriod), possibleEnd)
    }

    @Test
    fun `test getNextPossibleEndDate with min duration`() {
        val minDuration = Period.ofYears(1)
        val cancelInfo = Cancellation(minDuration = minDuration)
        val possibleEnd = cancelInfo.getNextPossibleEndDate(testStartDate, today)
        // contract started on the 1st January, has to end on 1st January
        assertEquals(LocalDate.of(2022, 1, 1), possibleEnd)
    }

    @Test
    fun `test getNextPossibleEndDate with notice period and min duration, long notice period`() {
        val noticePeriod = Period.ofMonths(12)
        val minDuration = Period.ofYears(1)
        val cancelInfo = Cancellation(noticePeriod = noticePeriod, minDuration = minDuration)
        val possibleEnd = cancelInfo.getNextPossibleEndDate(testStartDate, today)

        // contract started on the 1st January, has to end on 1st January
        assertEquals(1, possibleEnd!!.dayOfYear)
        // already too late for next year due to long notice period
        assertEquals(2023, possibleEnd.year)
    }

    @Test
    fun `test getNextPossibleEndDate with notice period and min duration, short notice period`() {
        val noticePeriod = Period.ofDays(2)
        val minDuration = Period.ofYears(1)
        val cancelInfo = Cancellation(noticePeriod = noticePeriod, minDuration = minDuration)
        val possibleEnd = cancelInfo.getNextPossibleEndDate(testStartDate, today)

        // contract started on the 1st January, has to end on 1st January
        assertEquals(1, possibleEnd!!.dayOfYear)
        // not too late for 2022
        assertEquals(2022, possibleEnd.year)
    }

    @Test
    fun `test getNextPossibleEndDate with notice period and min duration, min duration shorter then notice period`() {
        val noticePeriod = Period.ofMonths(2)
        val minDuration = Period.ofMonths(1)
        val cancelInfo = Cancellation(noticePeriod = noticePeriod, minDuration = minDuration)
        val possibleEnd = cancelInfo.getNextPossibleEndDate(testStartDate, today)

        // contract started on the 1st -> must end on the 1st!
        assertEquals(LocalDate.of(2021, 11, 1), possibleEnd)
    }

    @Test
    fun `test getNextPossibleEndDate with neither notice period nor min duration`() {
        val cancelInfo = Cancellation()
        val possibleEnd = cancelInfo.getNextPossibleEndDate(testStartDate, today)

        // can not get any useful information about possible end dates
        assertNull(possibleEnd)
    }

    @Test
    fun `test latest cancel date to cancel contract with min duration`() {
        val noticePeriod = Period.ofMonths(3)
        val minDuration = Period.ofYears(2)
        val cancelInfo = Cancellation(noticePeriod = noticePeriod, minDuration = minDuration)
        val cancelDate = cancelInfo.getLatestPossibleCancelDate(testStartDate, today)

        // 3 month before min duration end date
        assertEquals(LocalDate.of(2021, 10, 1), cancelDate)
    }

    @Test
    fun `test latest cancel date to cancel contract without min duration`() {
        val noticePeriod = Period.ofMonths(1)
        val cancelInfo = Cancellation(noticePeriod = noticePeriod)
        val cancelDate = cancelInfo.getLatestPossibleCancelDate(testStartDate, today)

        // no min duration -> can be canceled today
        assertEquals(today, cancelDate)
    }

    @Test
    fun `test latest cancel date to cancel contract in extension period`() {
        val noticePeriod = Period.ofMonths(2)
        val minDuration = Period.ofMonths(3)
        val extension = Period.ofYears(5)
        val cancelInfo = Cancellation(noticePeriod = noticePeriod, minDuration = minDuration, extendPeriod = extension)
        val cancelDate = cancelInfo.getLatestPossibleCancelDate(testStartDate, today)

        // 3 months min -> 03/01/2020 -> 5 years extension -> 03/01/2025 -> 2 month notice -> 02/01/2025
        assertEquals(LocalDate.of(2025, 2, 1), cancelDate)
    }
}