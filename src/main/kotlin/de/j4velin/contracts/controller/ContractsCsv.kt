package de.j4velin.contracts.controller

import de.j4velin.contracts.data.Contract
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.support.DefaultMessageSourceResolvable
import org.springframework.validation.BeanPropertyBindingResult
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.Period
import java.time.format.DateTimeParseException

/**
 * Exports contracts to CSV and imports them again, for example as a backup.
 *
 * The file has a header row with the column names of [COLUMNS], followed by one row per contract. Periods are ISO-8601
 * periods (like `P3M`), dates ISO-8601 dates (yyyy-MM-dd) and flags `true` or `false`. An empty cell means "not set".
 *
 * The import finds the columns by the names in the header row, so their order doesn't matter and unknown columns are
 * ignored. Each row is checked the same way as the contract form (see [ContractForm.toContract]).
 */
object ContractsCsv {

    /**
     * The columns of the CSV file. Only type and company are required on import
     */
    internal val COLUMNS = listOf(
        "type", "company", "notes", "startDate", "minDuration", "noticePeriod", "extendPeriod", "endDate", "canceled",
        "cancelAck", "bonusDescription", "bonusReceived"
    )

    private val REQUIRED_COLUMNS = listOf("type", "company")

    /**
     * Byte order mark: lets spreadsheet programs like Excel detect the UTF-8 encoding
     */
    private const val BOM = '﻿'

    internal const val ERROR_EMPTY = "contracts.import.error.empty"
    internal const val ERROR_COLUMN_MISSING = "contracts.import.error.columnMissing"
    internal const val ERROR_PERIOD = "contracts.import.error.period"
    internal const val ERROR_BOOLEAN = "contracts.import.error.boolean"
    internal const val ERROR_UNCLOSED_QUOTE = "contracts.import.error.unclosedQuote"

    /**
     * Thrown by [parse], if the file ends within a quoted cell: the closing quote is missing, and the cell would
     * otherwise silently take in all the following rows
     *
     * @property row the row, in which the unclosed quoted cell starts
     * @property completeRows the rows before that row, which were read completely
     */
    internal class UnclosedQuoteException(val row: Int, val completeRows: List<List<String>>) :
        IllegalArgumentException("Unclosed quote in row $row")

    /**
     * An invalid value in the imported file
     *
     * @property row the row number, counted the same as in a spreadsheet program: the header is row 1
     * @property column the name of the invalid column, or null if the error concerns the whole file
     * @property error the error message
     */
    data class ImportError(val row: Int, val column: String?, val error: MessageSourceResolvable)

    /**
     * The outcome of an import: either all [contracts] or, if any row is invalid, only the [errors]
     */
    data class ImportResult(val contracts: List<Contract>, val errors: List<ImportError>)

    /**
     * @param contracts the contracts to export
     * @return the CSV file, starting with a byte order mark
     */
    fun write(contracts: Iterable<Contract>): String = buildString {
        append(BOM)
        appendRow(COLUMNS)
        contracts.forEach { contract ->
            val cancellation = contract.cancellation
            appendRow(
                listOf(
                    contract.type,
                    contract.company,
                    contract.notes,
                    contract.startDate?.toString(),
                    cancellation.minDuration?.toString(),
                    cancellation.noticePeriod?.toString(),
                    cancellation.extendPeriod?.toString(),
                    cancellation.endDate?.toString(),
                    cancellation.canceled.toString(),
                    cancellation.ack.toString(),
                    contract.bonus.description,
                    contract.bonus.received.toString()
                )
            )
        }
    }

    /**
     * Reads contracts from a CSV file. Checks all rows and reports every invalid value instead of stopping at the
     * first one, so that the user can fix the whole file at once.
     *
     * The file is read as UTF-8 or, if it isn't valid UTF-8, as Windows-1252 (the encoding in which Excel saves CSV
     * files on Windows). The cells may be separated by commas or, as Excel does in many European locales, semicolons.
     *
     * @param bytes the content of the file
     * @param userId the id of the user, who imports the contracts. Becomes the owner of all of them
     * @return the contracts, or the errors, if any row is invalid. The contracts have no id, so saving them adds them
     */
    fun read(bytes: ByteArray, userId: String): ImportResult {
        // spreadsheet programs often add blank rows at the end
        val text = decode(bytes).removePrefix(BOM.toString())
        val rows = try {
            nonBlankRows(parse(text, detectSeparator(text)))
        } catch (e: UnclosedQuoteException) {
            return failure(ImportError(e.row, null, message(ERROR_UNCLOSED_QUOTE)))
        }
        val (headerRow, header) = rows.firstOrNull() ?: return failure(ImportError(1, null, message(ERROR_EMPTY)))
        val columnIndex = header.withIndex().associate { it.value.trim() to it.index }
        val missing = REQUIRED_COLUMNS.filterNot { it in columnIndex }
        if (missing.isNotEmpty()) {
            return failure(missing.map { ImportError(headerRow, it, message(ERROR_COLUMN_MISSING, it)) })
        }

        val contracts = mutableListOf<Contract>()
        val errors = mutableListOf<ImportError>()
        rows.drop(1).forEach { (rowNumber, row) ->
            val cell = { column: String -> columnIndex[column]?.let { row.getOrNull(it) }?.takeIf { it.isNotEmpty() } }
            val rowErrors = mutableListOf<ImportError>()
            val form = ContractForm().apply {
                type = cell("type") ?: ""
                company = cell("company") ?: ""
                notes = cell("notes")
                startDate = cell("startDate")
                readPeriod(cell("minDuration"), "minDuration", rowNumber, rowErrors).let {
                    minDurationValue = it.first; minDurationUnit = it.second
                }
                readPeriod(cell("noticePeriod"), "noticePeriod", rowNumber, rowErrors).let {
                    noticePeriodValue = it.first; noticePeriodUnit = it.second
                }
                readPeriod(cell("extendPeriod"), "extendPeriod", rowNumber, rowErrors).let {
                    extendPeriodValue = it.first; extendPeriodUnit = it.second
                }
                endDate = cell("endDate")
                canceled = readBoolean(cell("canceled"), "canceled", rowNumber, rowErrors)
                cancelAck = readBoolean(cell("cancelAck"), "cancelAck", rowNumber, rowErrors)
                bonusDescription = cell("bonusDescription")
                bonusReceived = readBoolean(cell("bonusReceived"), "bonusReceived", rowNumber, rowErrors)
            }
            val formErrors = BeanPropertyBindingResult(form, "contractForm")
            val contract = form.toContract(userId, null, formErrors)
            // the form's period fields are <name>Value and <name>Unit, the file has a single <name> column
            formErrors.fieldErrors.mapTo(rowErrors) {
                ImportError(rowNumber, it.field.removeSuffix("Value").removeSuffix("Unit"), it)
            }
            if (rowErrors.isEmpty() && contract != null) {
                contracts += contract
            }
            errors += rowErrors
        }
        return if (errors.isEmpty()) ImportResult(contracts, emptyList()) else failure(errors)
    }

    private fun failure(vararg errors: ImportError) = failure(errors.toList())

    private fun failure(errors: List<ImportError>) = ImportResult(emptyList(), errors)

    private fun message(code: String, vararg args: Any) = DefaultMessageSourceResolvable(arrayOf(code), args)

    /**
     * Converts an ISO-8601 period into the value and unit of the contract form. The form only supports periods of a
     * single unit, so periods like `P1M15D` are rejected. The form checks the amount
     *
     * @return the value and the unit, both null for no period or an invalid one
     */
    private fun readPeriod(value: String?, column: String, row: Int, errors: MutableList<ImportError>):
            Pair<String?, String?> {
        if (value.isNullOrBlank()) {
            return null to null
        }
        val period = try {
            Period.parse(value.trim())
        } catch (e: DateTimeParseException) {
            null
        }
        val formValues = ContractForm.toFormValues(period)
        // toFormValues only keeps the largest unit: if it dropped something, the period has more than one unit
        val singleUnit = formValues.first?.toIntOrNull()?.let { amount ->
            when (formValues.second) {
                "Y" -> Period.ofYears(amount)
                "M" -> Period.ofMonths(amount)
                "W" -> Period.ofWeeks(amount)
                else -> Period.ofDays(amount)
            }
        }
        if (period == null || singleUnit != period) {
            errors += ImportError(row, column, message(ERROR_PERIOD))
            return null to null
        }
        return formValues
    }

    /**
     * @return true for `true`, false for `false` or no value (ignoring the case)
     */
    private fun readBoolean(value: String?, column: String, row: Int, errors: MutableList<ImportError>): Boolean =
        when (value?.trim()?.lowercase()) {
            null, "", "false" -> false
            "true" -> true
            else -> {
                errors += ImportError(row, column, message(ERROR_BOOLEAN))
                false
            }
        }

    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1252"))
    }

    private fun StringBuilder.appendRow(cells: List<String?>) {
        cells.joinTo(this, ",") { escape(it ?: "") }
        append("\r\n")
    }

    /**
     * Quotes a cell, if it contains a separator, a quote or a line break (RFC 4180)
     */
    private fun escape(cell: String): String =
        if (cell.any { it == ',' || it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }

    /**
     * Splits a CSV file into rows and cells (RFC 4180): quoted cells may contain separators, line breaks and doubled
     * quotes
     *
     * @param separator the separator of the cells, see [detectSeparator]
     * @throws UnclosedQuoteException if the text ends within a quoted cell
     */
    internal fun parse(text: String, separator: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        // the row, in which the current quoted cell started
        var quoteRow = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    cell.append('"')
                    i++
                }

                c == '"' && (quoted || cell.isEmpty()) -> {
                    quoted = !quoted
                    quoteRow = rows.size + 1
                }

                quoted -> cell.append(c)
                c == separator -> {
                    row += cell.toString()
                    cell.clear()
                }

                c == '\r' || c == '\n' -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') {
                        i++
                    }
                    row += cell.toString()
                    cell.clear()
                    rows += row
                    row = mutableListOf()
                }

                else -> cell.append(c)
            }
            i++
        }
        if (quoted) {
            // within the quoted cell, line breaks don't end a row: all rows before it are complete
            throw UnclosedQuoteException(quoteRow, rows)
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }

    /**
     * @return the rows, which aren't blank, each with its row number (counted before skipping blank rows, so that the
     * number is the row as a spreadsheet program shows it)
     */
    private fun nonBlankRows(rows: List<List<String>>): List<IndexedValue<List<String>>> =
        rows.mapIndexed { index, row -> IndexedValue(index + 1, row) }
            .filterNot { (_, row) -> row.all { it.isBlank() } }

    /**
     * Detects the separator by reading the header (the first row, which isn't blank) with each of them: column names
     * may contain the other separator, quoted or not, so counting separators isn't reliable
     *
     * @return the separator, with which the header has all required columns. Otherwise, the one, with which it has
     * more of the known [COLUMNS], and a comma, if that's a tie (the file is invalid then anyway)
     */
    internal fun detectSeparator(text: String): Char {
        val headers = SEPARATORS.associateWith { separator ->
            // an unclosed quote is reported when reading the file with the detected separator. Whether a quote opens a
            // quoted cell depends on the separator, so the header is still needed to detect it
            val rows = try {
                parse(text, separator)
            } catch (e: UnclosedQuoteException) {
                e.completeRows
            }
            nonBlankRows(rows).firstOrNull()?.value.orEmpty().map { it.trim() }
        }
        return headers.entries.firstOrNull { (_, header) -> header.containsAll(REQUIRED_COLUMNS) }?.key
            ?: headers.maxBy { (_, header) -> header.count { it in COLUMNS } }.key
    }

    /**
     * The separators of the supported files, the default first: commas, as written by [write], or semicolons, as
     * saved by Excel in many European locales
     */
    private val SEPARATORS = listOf(',', ';')
}
