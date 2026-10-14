package de.j4velin.contracts.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration
import java.time.LocalDate
import java.time.Period

class ContractTest {

    private val today = LocalDate.of(2026, 10, 13)

    private fun contract(company: String, startDate: LocalDate? = null, cancellation: Cancellation = Cancellation()) =
        Contract("user", "type", company, startDate = startDate, cancellation = cancellation)

    @Test
    fun `test getSortDate prefers the latest cancel date`() {
        val withNotice = contract(
            "a", LocalDate.of(2026, 1, 1),
            Cancellation(minDuration = Period.ofYears(1), noticePeriod = Period.ofMonths(1))
        )
        assertEquals(LocalDate.of(2026, 12, 1), withNotice.getSortDate(today))

        val withoutNotice = contract("b", LocalDate.of(2026, 1, 1), Cancellation(minDuration = Period.ofYears(1)))
        assertEquals(LocalDate.of(2027, 1, 1), withoutNotice.getSortDate(today))

        val canceled = contract("c", cancellation = Cancellation(noticePeriod = Period.ofMonths(1), endDate = today))
        assertEquals(today, canceled.getSortDate(today))

        assertNull(contract("d").getSortDate(today))
    }

    @Test
    fun `test sortedByDate sorts like the natural order`() {
        val contracts = listOf(
            contract("no info 1"),
            contract("ends next year", LocalDate.of(2026, 1, 1), Cancellation(minDuration = Period.ofYears(1))),
            contract("cancel today", cancellation = Cancellation(noticePeriod = Period.ofDays(3))),
            contract("no info 2"),
            contract("canceled", cancellation = Cancellation(endDate = LocalDate.of(2026, 11, 30))),
            contract(
                "cancel in december", LocalDate.of(2026, 1, 1),
                Cancellation(minDuration = Period.ofYears(1), noticePeriod = Period.ofMonths(1))
            )
        )

        val sorted = Contract.sortedByDate(contracts, today)

        assertEquals(
            listOf("cancel today", "canceled", "cancel in december", "ends next year", "no info 1", "no info 2"),
            sorted.map { it.company }
        )
    }

    @Test
    fun `test sortedByDate sorts like compareTo`() {
        // dates far in the past or in the future, so that compareTo's LocalDate.now() doesn't change the order
        val contracts = listOf(
            contract("none"),
            contract("2100", cancellation = Cancellation(endDate = LocalDate.of(2100, 1, 1))),
            contract("2000", cancellation = Cancellation(endDate = LocalDate.of(2000, 1, 1))),
            contract("3000", LocalDate.of(2999, 1, 1), Cancellation(minDuration = Period.ofYears(1)))
        )
        assertEquals(contracts.sorted(), Contract.sortedByDate(contracts))
        assertEquals(listOf("2000", "2100", "3000", "none"), contracts.sorted().map { it.company })
    }

    @Test
    fun `test sortedByDate is fast for many long running contracts with short terms`() {
        val contracts = (1..500).map {
            contract(
                "c$it", LocalDate.of(1000, 1, 1).plusDays(it.toLong()),
                Cancellation(minDuration = Period.ofDays(1), extendPeriod = Period.ofDays(1))
            )
        }
        // a coarse limit, so a busy CI runner can't fail it; the old sort needs billions of steps for this list
        val sorted = assertTimeoutPreemptively(Duration.ofSeconds(5)) { Contract.sortedByDate(contracts, today) }
        assertEquals(contracts.size, sorted.size)
    }
}
