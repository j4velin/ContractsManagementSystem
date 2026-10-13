package de.j4velin.contracts.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.Period

class CancellationFromDtoTest {

    private fun dto(
        minDurationValue: String? = null,
        minDurationUnit: Char? = null,
        noticePeriodValue: String? = null,
        noticePeriodUnit: Char? = null,
        extendPeriodValue: String? = null,
        extendPeriodUnit: Char? = null,
        startDate: String? = null,
        endDate: String? = null
    ) = ContractDTO(
        type = "type",
        company = "company",
        minDurationValue = minDurationValue,
        minDurationUnit = minDurationUnit,
        noticePeriodValue = noticePeriodValue,
        noticePeriodUnit = noticePeriodUnit,
        extendPeriodValue = extendPeriodValue,
        extendPeriodUnit = extendPeriodUnit,
        startDate = startDate,
        endDate = endDate
    )

    private fun assertBadRequest(block: () -> Unit) {
        val e = assertThrows<ResponseStatusException> { block() }
        assertEquals(HttpStatus.BAD_REQUEST, e.status)
    }

    @Test
    fun `no unit selected gives null periods`() {
        val cancellation = Cancellation.fromDTO(dto(minDurationValue = "3", noticePeriodValue = "1"))
        assertNull(cancellation.minDuration)
        assertNull(cancellation.noticePeriod)
        assertNull(cancellation.extendPeriod)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `unit with empty value gives null`(value: String) {
        val cancellation = Cancellation.fromDTO(
            dto(
                minDurationValue = value, minDurationUnit = 'M',
                noticePeriodValue = value, noticePeriodUnit = 'W',
                extendPeriodValue = value, extendPeriodUnit = 'Y'
            )
        )
        assertNull(cancellation.minDuration)
        assertNull(cancellation.noticePeriod)
        assertNull(cancellation.extendPeriod)
    }

    @Test
    fun `unit with null value gives null`() {
        assertNull(Cancellation.fromDTO(dto(minDurationUnit = 'M')).minDuration)
    }

    @Test
    fun `valid values are parsed`() {
        val cancellation = Cancellation.fromDTO(
            dto(
                minDurationValue = "24", minDurationUnit = 'M',
                noticePeriodValue = "3", noticePeriodUnit = 'W',
                extendPeriodValue = "1", extendPeriodUnit = 'Y'
            )
        )
        assertEquals(Period.ofMonths(24), cancellation.minDuration)
        assertEquals(Period.ofWeeks(3), cancellation.noticePeriod)
        assertEquals(Period.ofYears(1), cancellation.extendPeriod)
    }

    @Test
    fun `days unit and surrounding whitespace are accepted`() {
        val cancellation = Cancellation.fromDTO(dto(noticePeriodValue = " 14 ", noticePeriodUnit = 'D'))
        assertEquals(Period.ofDays(14), cancellation.noticePeriod)
    }

    @Test
    fun `zero is allowed`() {
        val cancellation = Cancellation.fromDTO(
            dto(noticePeriodValue = "0", noticePeriodUnit = 'D', extendPeriodValue = "0", extendPeriodUnit = 'M')
        )
        assertEquals(Period.ofDays(0), cancellation.noticePeriod)
        assertEquals(Period.ofMonths(0), cancellation.extendPeriod)
    }

    @ParameterizedTest
    @ValueSource(strings = ["-1", "1.5", "1,5", "abc", "1e3", "+1", "0x10", "1000", "99999999999"])
    fun `invalid values are rejected with bad request`(value: String) {
        assertBadRequest { Cancellation.fromDTO(dto(minDurationValue = value, minDurationUnit = 'M')) }
        assertBadRequest { Cancellation.fromDTO(dto(noticePeriodValue = value, noticePeriodUnit = 'D')) }
        assertBadRequest { Cancellation.fromDTO(dto(extendPeriodValue = value, extendPeriodUnit = 'Y')) }
    }

    @Test
    fun `maximum value is accepted`() {
        val cancellation = Cancellation.fromDTO(dto(minDurationValue = "999", minDurationUnit = 'Y'))
        assertEquals(Period.ofYears(Cancellation.MAX_PERIOD_AMOUNT), cancellation.minDuration)
    }

    @Test
    fun `overflowing week value is rejected with bad request`() {
        assertBadRequest {
            Cancellation.fromDTO(dto(noticePeriodValue = Int.MAX_VALUE.toString(), noticePeriodUnit = 'W'))
        }
    }

    @ParameterizedTest
    @ValueSource(chars = ['X', 'd', 'H', '1'])
    fun `unknown unit is rejected with bad request`(unit: Char) {
        assertBadRequest { Cancellation.fromDTO(dto(minDurationValue = "1", minDurationUnit = unit)) }
    }

    @Test
    fun `valid end date is parsed`() {
        val cancellation = Cancellation.fromDTO(dto(endDate = "2021-08-22"))
        assertEquals(LocalDate.of(2021, 8, 22), cancellation.endDate)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `blank end date gives null`(value: String) {
        assertNull(Cancellation.fromDTO(dto(endDate = value)).endDate)
    }

    @ParameterizedTest
    @ValueSource(strings = ["garbage", "2021-13-01", "22.08.2021", "2021-02-30"])
    fun `malformed end date is rejected with bad request`(value: String) {
        assertBadRequest { Cancellation.fromDTO(dto(endDate = value)) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["+999999999-12-31", "+10000-01-01", "0999-12-31", "-0001-01-01"])
    fun `dates outside of four-digit years are rejected with bad request`(value: String) {
        assertBadRequest { Cancellation.fromDTO(dto(endDate = value)) }
        assertBadRequest { Contract(dto(startDate = value), "user") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["garbage", "2021-13-01", "22.08.2021"])
    fun `malformed start date is rejected with bad request`(value: String) {
        assertBadRequest { Contract(dto(startDate = value), "user") }
    }

    @Test
    fun `valid start date is parsed`() {
        assertEquals(LocalDate.of(2000, 1, 1), Contract(dto(startDate = "2000-01-01"), "user").startDate)
    }
}
