package de.j4velin.contracts.controller

import de.j4velin.contracts.data.Bonus
import de.j4velin.contracts.data.Cancellation
import de.j4velin.contracts.data.Contract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindingResult
import java.time.LocalDate
import java.time.Period

class ContractFormTest {

    /**
     * @return a valid form (type and company set), changed by [block]
     */
    private fun form(block: ContractForm.() -> Unit = {}) = ContractForm().apply {
        type = "type"
        company = "company"
        block()
    }

    private fun errorsOf(form: ContractForm) = BeanPropertyBindingResult(form, "contractForm")

    /**
     * Converts the form and asserts that it has no errors
     *
     * @return the converted contract
     */
    private fun convert(form: ContractForm, id: String? = null): Contract {
        val errors = errorsOf(form)
        val contract = form.toContract("user", id, errors)
        assertFalse(errors.hasErrors(), errors.toString())
        return contract ?: fail(errors.toString())
    }

    /**
     * Converts the form and asserts that it is rejected with exactly one error: [code] at [field]
     *
     * @return the errors
     */
    private fun assertFieldError(form: ContractForm, field: String, code: String): BindingResult {
        val errors = errorsOf(form)
        assertNull(form.toContract("user", null, errors))
        assertEquals(1, errors.errorCount, errors.toString())
        val error = errors.getFieldError(field) ?: fail(errors.toString())
        assertEquals(code, error.code)
        return errors
    }

    // --- type and company

    @Test
    fun `type and company are stored trimmed`() {
        val contract = convert(form { type = "  Rent "; company = " ACME  " })
        assertEquals("Rent", contract.type)
        assertEquals("ACME", contract.company)
        assertEquals("user", contract.userId)
    }

    @ParameterizedTest
    @CsvSource("'', company, type", "'   ', company, type", "type, '', company", "type, '  ', company")
    fun `blank type or company is required`(type: String, company: String, field: String) {
        assertFieldError(form { this.type = type; this.company = company }, field, ContractForm.ERROR_REQUIRED)
    }

    @Test
    fun `all invalid fields are reported at once`() {
        val form = form {
            type = ""
            company = " "
            noticePeriodValue = "-1"
            noticePeriodUnit = "M"
            endDate = "garbage"
        }
        val errors = errorsOf(form)
        assertNull(form.toContract("user", null, errors))
        assertEquals(setOf("type", "company", "noticePeriodValue", "endDate"), errors.fieldErrors.map { it.field }.toSet())
    }

    @Test
    fun `existing binding errors prevent the conversion`() {
        val form = form()
        val errors = errorsOf(form)
        errors.rejectValue("notes", "typeMismatch")
        assertNull(form.toContract("user", null, errors))
    }

    @Test
    fun `the id and the notes are passed on`() {
        val contract = convert(form { notes = " some notes " }, id = "c1")
        assertEquals("c1", contract.id)
        assertEquals(" some notes ", contract.notes)
    }

    // --- periods

    @Test
    fun `no unit selected gives null periods`() {
        val cancellation = convert(form { minDurationValue = "3"; noticePeriodValue = "1"; extendPeriodUnit = "" })
            .cancellation
        assertNull(cancellation.minDuration)
        assertNull(cancellation.noticePeriod)
        assertNull(cancellation.extendPeriod)
    }

    @Test
    fun `no unit selected ignores an invalid value`() {
        assertNull(convert(form { minDurationValue = "garbage" }).cancellation.minDuration)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `unit with empty value gives null`(value: String) {
        val cancellation = convert(form {
            minDurationValue = value; minDurationUnit = "M"
            noticePeriodValue = value; noticePeriodUnit = "W"
            extendPeriodValue = value; extendPeriodUnit = "Y"
        }).cancellation
        assertNull(cancellation.minDuration)
        assertNull(cancellation.noticePeriod)
        assertNull(cancellation.extendPeriod)
    }

    @Test
    fun `unit with null value gives null`() {
        assertNull(convert(form { minDurationUnit = "M" }).cancellation.minDuration)
    }

    @Test
    fun `valid values are parsed`() {
        val cancellation = convert(form {
            minDurationValue = "24"; minDurationUnit = "M"
            noticePeriodValue = "3"; noticePeriodUnit = "W"
            extendPeriodValue = "1"; extendPeriodUnit = "Y"
        }).cancellation
        assertEquals(Period.ofMonths(24), cancellation.minDuration)
        assertEquals(Period.ofWeeks(3), cancellation.noticePeriod)
        assertEquals(Period.ofYears(1), cancellation.extendPeriod)
    }

    @Test
    fun `days unit and surrounding whitespace are accepted`() {
        val cancellation = convert(form { noticePeriodValue = " 14 "; noticePeriodUnit = "D" }).cancellation
        assertEquals(Period.ofDays(14), cancellation.noticePeriod)
    }

    @Test
    fun `zero is allowed`() {
        val cancellation = convert(form {
            noticePeriodValue = "0"; noticePeriodUnit = "D"
            extendPeriodValue = "0"; extendPeriodUnit = "M"
        }).cancellation
        assertEquals(Period.ofDays(0), cancellation.noticePeriod)
        assertEquals(Period.ofMonths(0), cancellation.extendPeriod)
    }

    @Test
    fun `an empty extension period stays empty instead of defaulting to the minimum duration`() {
        val cancellation = convert(form { minDurationValue = "2"; minDurationUnit = "Y" }).cancellation
        assertEquals(Period.ofYears(2), cancellation.minDuration)
        assertNull(cancellation.extendPeriod)
    }

    @ParameterizedTest
    @ValueSource(strings = ["-1", "1.5", "1,5", "abc", "1e3", "+1", "0x10", "1000", "99999999999"])
    fun `invalid values are rejected at the value field`(value: String) {
        assertFieldError(
            form { minDurationValue = value; minDurationUnit = "M" }, "minDurationValue", ContractForm.ERROR_PERIOD_AMOUNT
        )
        assertFieldError(
            form { noticePeriodValue = value; noticePeriodUnit = "D" }, "noticePeriodValue", ContractForm.ERROR_PERIOD_AMOUNT
        )
        assertFieldError(
            form { extendPeriodValue = value; extendPeriodUnit = "Y" }, "extendPeriodValue", ContractForm.ERROR_PERIOD_AMOUNT
        )
    }

    @Test
    fun `the value error names the maximum`() {
        val errors = assertFieldError(
            form { noticePeriodValue = "1000"; noticePeriodUnit = "D" }, "noticePeriodValue",
            ContractForm.ERROR_PERIOD_AMOUNT
        )
        assertEquals(listOf(ContractForm.MAX_PERIOD_AMOUNT), errors.getFieldError("noticePeriodValue")!!.arguments!!.toList())
    }

    @Test
    fun `an invalid value is kept as entered`() {
        val form = form { noticePeriodValue = "1.5"; noticePeriodUnit = "M" }
        assertFieldError(form, "noticePeriodValue", ContractForm.ERROR_PERIOD_AMOUNT)
        assertEquals("1.5", form.noticePeriodValue)
    }

    @Test
    fun `maximum value is accepted`() {
        val cancellation = convert(form { minDurationValue = "999"; minDurationUnit = "Y" }).cancellation
        assertEquals(Period.ofYears(ContractForm.MAX_PERIOD_AMOUNT), cancellation.minDuration)
    }

    @Test
    fun `overflowing week value is rejected`() {
        assertFieldError(
            form { noticePeriodValue = Int.MAX_VALUE.toString(); noticePeriodUnit = "W" },
            "noticePeriodValue", ContractForm.ERROR_PERIOD_AMOUNT
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["X", "d", "H", "1", " ", "DD"])
    fun `unknown unit is rejected at the unit field`(unit: String) {
        assertFieldError(form { minDurationValue = "1"; minDurationUnit = unit }, "minDurationUnit", ContractForm.ERROR_PERIOD_UNIT)
    }

    @Test
    fun `invalid value and unknown unit are both reported`() {
        val form = form { extendPeriodValue = "-1"; extendPeriodUnit = "X" }
        val errors = errorsOf(form)
        assertNull(form.toContract("user", null, errors))
        assertEquals(ContractForm.ERROR_PERIOD_AMOUNT, errors.getFieldError("extendPeriodValue")?.code)
        assertEquals(ContractForm.ERROR_PERIOD_UNIT, errors.getFieldError("extendPeriodUnit")?.code)
    }

    // --- dates

    @Test
    fun `valid end date is parsed`() {
        assertEquals(LocalDate.of(2021, 8, 22), convert(form { endDate = "2021-08-22" }).cancellation.endDate)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `blank end date gives null`(value: String) {
        assertNull(convert(form { endDate = value }).cancellation.endDate)
    }

    @ParameterizedTest
    @ValueSource(strings = ["garbage", "2021-13-01", "22.08.2021", "2021-02-30"])
    fun `malformed end date is rejected`(value: String) {
        assertFieldError(form { endDate = value }, "endDate", ContractForm.ERROR_DATE)
    }

    @ParameterizedTest
    @ValueSource(strings = ["+999999999-12-31", "+10000-01-01", "0999-12-31", "-0001-01-01"])
    fun `dates outside of four-digit years are rejected`(value: String) {
        assertFieldError(form { endDate = value }, "endDate", ContractForm.ERROR_DATE_YEAR)
        assertFieldError(form { startDate = value }, "startDate", ContractForm.ERROR_DATE_YEAR)
    }

    @ParameterizedTest
    @ValueSource(strings = ["garbage", "2021-13-01", "22.08.2021"])
    fun `malformed start date is rejected`(value: String) {
        assertFieldError(form { startDate = value }, "startDate", ContractForm.ERROR_DATE)
    }

    @Test
    fun `valid start date is parsed`() {
        assertEquals(LocalDate.of(2000, 1, 1), convert(form { startDate = "2000-01-01" }).startDate)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `blank start date gives null`(value: String) {
        assertNull(convert(form { startDate = value }).startDate)
    }

    // --- canceled and acknowledged

    @Test
    fun `nothing checked is not canceled`() {
        val cancellation = convert(form()).cancellation
        assertFalse(cancellation.canceled)
        assertFalse(cancellation.ack)
    }

    @Test
    fun `a set end date implies canceled`() {
        val cancellation = convert(form { endDate = "2021-08-22" }).cancellation
        assertTrue(cancellation.canceled)
        assertFalse(cancellation.ack)
    }

    @Test
    fun `canceled can be checked without an end date`() {
        assertTrue(convert(form { canceled = true }).cancellation.canceled)
    }

    @Test
    fun `acknowledged needs the canceled box`() {
        assertTrue(convert(form { canceled = true; cancelAck = true }).cancellation.ack)
        assertFalse(convert(form { cancelAck = true }).cancellation.ack)
        // an end date implies canceled, but not that the canceled box was checked
        val withEndDate = convert(form { cancelAck = true; endDate = "2021-08-22" }).cancellation
        assertTrue(withEndDate.canceled)
        assertFalse(withEndDate.ack)
    }

    // --- bonus

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\n"])
    fun `blank bonus description gives null`(value: String) {
        assertNull(convert(form { bonusDescription = value }).bonus.description)
    }

    @Test
    fun `bonus description is stored as entered`() {
        assertEquals(" cashback ", convert(form { bonusDescription = " cashback " }).bonus.description)
    }

    @Test
    fun `checked bonus box means received`() {
        assertTrue(convert(form { bonusReceived = true }).bonus.received)
        assertFalse(convert(form()).bonus.received)
    }

    // --- prefilling from a contract

    @Test
    fun `form is prefilled from a contract`() {
        val contract = Contract(
            "user", "Rent", "ACME", notes = "notes", startDate = LocalDate.of(2020, 1, 15),
            cancellation = Cancellation(
                minDuration = Period.ofYears(2), noticePeriod = Period.ofWeeks(3), extendPeriod = Period.ofMonths(1),
                endDate = LocalDate.of(2027, 1, 14), canceled = true, ack = true
            ),
            bonus = Bonus("cashback", received = true),
            id = "c1"
        )
        val form = ContractForm.from(contract)
        assertEquals("Rent", form.type)
        assertEquals("ACME", form.company)
        assertEquals("notes", form.notes)
        assertEquals("2020-01-15", form.startDate)
        assertEquals("2" to "Y", form.minDurationValue to form.minDurationUnit)
        assertEquals("3" to "W", form.noticePeriodValue to form.noticePeriodUnit)
        assertEquals("1" to "M", form.extendPeriodValue to form.extendPeriodUnit)
        assertEquals("2027-01-14", form.endDate)
        assertTrue(form.canceled)
        assertTrue(form.cancelAck)
        assertEquals("cashback", form.bonusDescription)
        assertTrue(form.bonusReceived)
        assertTrue(form.hasCancellationInput())
        assertTrue(form.hasBonusInput())
        // and converts back into the same contract
        assertEquals(contract, convert(form, id = "c1"))
    }

    @Test
    fun `form of a contract without optional values is empty`() {
        val form = ContractForm.from(Contract("user", "Rent", "ACME", id = "c1"))
        assertNull(form.startDate)
        assertNull(form.minDurationValue)
        assertNull(form.minDurationUnit)
        assertNull(form.endDate)
        assertFalse(form.canceled)
        assertFalse(form.hasCancellationInput())
        assertFalse(form.hasBonusInput())
    }

    @ParameterizedTest
    @CsvSource(
        "P2Y, 2, Y", "P1Y6M, 1, Y", "P18M, 18, M", "P3W, 3, W", "P14D, 2, W", "P10D, 10, D", "P0D, 0, D",
        "P1M1D, 1, M"
    )
    fun `periods are shown in their unit, whole weeks as weeks`(period: String, value: String, unit: String) {
        assertEquals(value to unit, ContractForm.toFormValues(Period.parse(period)))
    }

    @Test
    fun `no period gives empty fields`() {
        assertEquals(null to null, ContractForm.toFormValues(null))
    }
}
