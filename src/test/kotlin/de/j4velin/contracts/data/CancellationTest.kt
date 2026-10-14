package de.j4velin.contracts.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Period

/**
 * Tests the cancellation state. The date arithmetic is tested in [NoticeScheduleTest]
 */
class CancellationTest {

    private val today = LocalDate.of(2021, 8, 22)

    @Test
    fun `test toggle cancel`() {
        val cancelInfo = Cancellation(noticePeriod = Period.ofDays(1))
        assertFalse(cancelInfo.canceled)
        assertFalse(cancelInfo.ack)
        assertNull(cancelInfo.endDate)
        val contract = Contract("id", "type", "company", cancellation = cancelInfo)
        val newContract = contract.toggleCancel(today)
        assertTrue(newContract.cancellation.canceled)
        assertNotNull(newContract.cancellation.endDate)
        assertFalse(newContract.cancellation.ack) // ack should not be affected by toggling cancel state
    }

    @Test
    fun `test toggle cancel twice clears the end date`() {
        val cancelInfo = Cancellation(noticePeriod = Period.ofDays(1))
        val contract = Contract("id", "type", "company", cancellation = cancelInfo)
        val canceled = contract.toggleCancel(today)
        assertNotNull(canceled.cancellation.endDate)
        assertNull(canceled.cancellation.getLatestPossibleCancelDate(null, today))

        val unCanceled = canceled.toggleCancel(today)
        assertFalse(unCanceled.cancellation.canceled)
        assertFalse(unCanceled.cancellation.ack)
        assertNull(unCanceled.cancellation.endDate)
        // the dates are calculated again instead of being frozen at the end date set when canceling
        assertEquals(today, unCanceled.cancellation.getLatestPossibleCancelDate(null, today))
        assertEquals(today.plusDays(1), unCanceled.cancellation.getNextPossibleEndDate(null, today))
        // and the contract is back in its original state
        assertEquals(contract, unCanceled)
    }

    @Test
    fun `test toggle cancel on acknowledged cancellation clears end date and ack`() {
        val startDate = LocalDate.of(2020, 3, 15)
        val cancelInfo = Cancellation(noticePeriod = Period.ofMonths(1), minDuration = Period.ofYears(1))
        val contract = Contract("id", "type", "company", startDate = startDate, cancellation = cancelInfo)
        val canceled = contract.toggleCancelAck(today)
        assertTrue(canceled.cancellation.canceled)
        assertTrue(canceled.cancellation.ack)

        val unCanceled = canceled.toggleCancel(today)
        assertFalse(unCanceled.cancellation.canceled)
        assertFalse(unCanceled.cancellation.ack)
        assertNull(unCanceled.cancellation.endDate)
        assertEquals(LocalDate.of(2022, 2, 15), unCanceled.cancellation.getLatestPossibleCancelDate(startDate, today))
    }

    @Test
    fun `test toggle cancel keeps a manually set end date`() {
        val endDate = LocalDate.of(2030, 6, 30)
        // not possible through the form (a set end date implies 'canceled'), but might exist in the database
        val cancelInfo = Cancellation(noticePeriod = Period.ofDays(1), endDate = endDate, canceled = false)
        val contract = Contract("id", "type", "company", cancellation = cancelInfo)
        val canceled = contract.toggleCancel(today)
        assertTrue(canceled.cancellation.canceled)
        assertEquals(endDate, canceled.cancellation.endDate)
    }

    @Test
    fun `test hasCancellationInfo`() {
        assertFalse(Cancellation().hasCancellationInfo())
        assertTrue(Cancellation(minDuration = Period.ofMonths(1)).hasCancellationInfo())
        assertTrue(Cancellation(noticePeriod = Period.ofMonths(1)).hasCancellationInfo())
        // only an extension period set -> the edit form must still show the cancellation section
        assertTrue(Cancellation(extendPeriod = Period.ofMonths(1)).hasCancellationInfo())
        assertTrue(Cancellation(endDate = today).hasCancellationInfo())
        assertTrue(Cancellation(canceled = true).hasCancellationInfo())
    }

    @Test
    fun `test toggle cancel ack`() {
        val cancelInfo = Cancellation(noticePeriod = Period.ofDays(1))
        assertFalse(cancelInfo.canceled)
        assertFalse(cancelInfo.ack)
        assertNull(cancelInfo.endDate)
        val contract = Contract("id", "type", "company", cancellation = cancelInfo)
        val newContract = contract.toggleCancelAck(today)
        assertTrue(newContract.cancellation.canceled) // should also 'cancel' the contract
        assertNotNull(newContract.cancellation.endDate)
        assertTrue(newContract.cancellation.ack)
    }

    @Test
    fun `test date functions use the notice schedule unless an end date is set`() {
        val startDate = LocalDate.of(2024, 1, 31)
        val cancelInfo = Cancellation(
            minDuration = Period.ofMonths(1), noticePeriod = Period.ofMonths(1), extendPeriod = Period.ofMonths(1)
        )
        assertEquals(
            NoticeSchedule(
                startDate = startDate,
                minDuration = Period.ofMonths(1),
                extension = Period.ofMonths(1),
                noticePeriod = Period.ofMonths(1)
            ),
            cancelInfo.noticeSchedule(startDate)
        )
        val now = LocalDate.of(2026, 3, 31)
        assertEquals(LocalDate.of(2026, 5, 31), cancelInfo.getNextPossibleEndDate(startDate, now))
        assertEquals(LocalDate.of(2026, 4, 30), cancelInfo.getLatestPossibleCancelDate(startDate, now))

        // a null extension (ContractForm, old documents) is passed on, the schedule falls back to the min duration
        val withoutExtension = cancelInfo.copy(extendPeriod = null)
        assertNull(withoutExtension.noticeSchedule(startDate).extension)
        assertEquals(LocalDate.of(2026, 5, 31), withoutExtension.getNextPossibleEndDate(startDate, now))

        // an end date overrides the calculated dates
        val endDate = LocalDate.of(2030, 6, 30)
        val withEndDate = cancelInfo.copy(endDate = endDate)
        assertEquals(endDate, withEndDate.getNextPossibleEndDate(startDate, now))
        assertNull(withEndDate.getLatestPossibleCancelDate(startDate, now))
    }
}
