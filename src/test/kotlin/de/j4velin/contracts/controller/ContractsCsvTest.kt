package de.j4velin.contracts.controller

import de.j4velin.contracts.data.Bonus
import de.j4velin.contracts.data.Cancellation
import de.j4velin.contracts.data.Contract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.time.Period

class ContractsCsvTest {

    private val userId = "user1"

    private fun read(csv: String) = ContractsCsv.read(csv.toByteArray(Charsets.UTF_8), userId)

    @Test
    fun `exported contracts are imported again unchanged, except for the id`() {
        val contracts = listOf(
            Contract(
                userId, "Mobile", "Telco, Inc.", notes = "line 1\nline 2 with \"quotes\"; and more",
                startDate = LocalDate.of(2024, 1, 31),
                cancellation = Cancellation(
                    minDuration = Period.ofYears(2), noticePeriod = Period.ofWeeks(3), extendPeriod = Period.ofMonths(1),
                    endDate = LocalDate.of(2026, 1, 31), canceled = true, ack = true
                ),
                bonus = Bonus("100 € cashback", received = true),
                id = "c1"
            ),
            Contract(userId, "Streaming", "Flix", id = "c2"),
            Contract(userId, "Gym", "Muscles", cancellation = Cancellation(minDuration = Period.ofMonths(12)), id = "c3")
        )

        val result = ContractsCsv.read(ContractsCsv.write(contracts).toByteArray(Charsets.UTF_8), userId)

        assertEquals(emptyList<ContractsCsv.ImportError>(), result.errors)
        assertEquals(contracts.map { it.copy(id = null) }, result.contracts)
    }

    @Test
    fun `export has a header row and a byte order mark`() {
        val csv = ContractsCsv.write(listOf(Contract(userId, "type", "company")))
        assertEquals(
            "﻿type,company,notes,startDate,minDuration,noticePeriod,extendPeriod,endDate,canceled,cancelAck," +
                    "bonusDescription,bonusReceived\r\ntype,company,,,,,,,false,false,,false\r\n",
            csv
        )
    }

    @Test
    fun `imports semicolon separated Windows-1252 files with columns in any order and only the required ones`() {
        val csv = "company;type;notes;unknown\r\nMüller;Rent;;x\r\n;;;\r\n"
        val result = ContractsCsv.read(csv.toByteArray(charset("windows-1252")), userId)
        assertEquals(listOf(Contract(userId, "Rent", "Müller")), result.contracts)
    }

    @Test
    fun `reports every invalid value with its row and column and imports nothing`() {
        val csv = """
            type,company,startDate,minDuration,noticePeriod,canceled
            ok,ok,,,,
            ,ok,2024-13-01,P1M2D,P1000D,maybe
        """.trimIndent()

        val result = read(csv)

        assertTrue(result.contracts.isEmpty())
        assertEquals(
            listOf(
                3 to "minDuration" to ContractsCsv.ERROR_PERIOD,
                3 to "canceled" to ContractsCsv.ERROR_BOOLEAN,
                3 to "type" to ContractForm.ERROR_REQUIRED,
                3 to "startDate" to ContractForm.ERROR_DATE,
                3 to "noticePeriod" to ContractForm.ERROR_PERIOD_AMOUNT
            ),
            // a field error's codes start with the most specific one, like "contract.error.date.contractForm.startDate"
            result.errors.map { it.row to it.column to it.error.codes!!.last() }
        )
    }

    @Test
    fun `error rows are counted like in a spreadsheet program, including blank rows`() {
        val csv = "type,company\r\n,,\r\nRent,ACME\r\n\r\nRent,\r\n"
        assertEquals(listOf(5 to "company"), read(csv).errors.map { it.row to it.column })
    }

    @Test
    fun `missing columns are reported in the row of the header, after leading blank rows`() {
        assertEquals(listOf(3 to "company"), read(";;\r\n\r\ntype;notes\r\nRent;\r\n").errors.map { it.row to it.column })
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "type;company;\"unknown,column\"\r\n", "type;company;unknown,column\r\n", ";;\r\n\r\ntype;company;x\r\n",
            "\"x\r\ny,z\";type;company\r\n"
        ]
    )
    fun `separator is the one, with which the header has the required columns`(header: String) {
        assertEquals(';', ContractsCsv.detectSeparator(header))
        val columns = ContractsCsv.parse(header, ';').last { row -> row.any { it.isNotBlank() } }
        val row = columns.joinToString(";") { if (it == "type") "Rent" else if (it == "company") "ACME" else "" }
        val result = read(header + row + "\r\n")
        assertEquals(emptyList<ContractsCsv.ImportError>(), result.errors)
        assertEquals(listOf(Contract(userId, "Rent", "ACME")), result.contracts)
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = ["type,company,a;b|,", "type,company,\"a;b\"|,", "type;notes|;", "notes,x;y|,", "|,", "unknown|,"]
    )
    fun `separator without all required columns is the one with more known columns, by default a comma`(
        header: String?, separator: Char
    ) {
        assertEquals(separator, ContractsCsv.detectSeparator(header ?: ""))
    }

    @Test
    fun `reports missing required columns`() {
        val result = read("type,notes\r\nRent,\r\n")
        assertEquals(listOf("company"), result.errors.map { it.column })
        assertEquals(ContractsCsv.ERROR_COLUMN_MISSING, result.errors.single().error.codes!!.first())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "﻿", "\r\n\r\n"])
    fun `reports an empty file`(csv: String) {
        assertEquals(ContractsCsv.ERROR_EMPTY, read(csv).errors.single().error.codes!!.first())
    }

    @Test
    fun `parses quoted cells with separators, doubled quotes and line breaks`() {
        assertEquals(
            listOf(listOf("a,b", "say \"hi\"", "x\r\ny", ""), listOf("last")),
            ContractsCsv.parse("\"a,b\",\"say \"\"hi\"\"\",\"x\r\ny\",\"\"\nlast")
        )
    }
}
