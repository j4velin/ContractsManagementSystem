package de.j4velin.contracts.data

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Document
data class Contract internal constructor(
    val userId: String,
    val type: String,
    val company: String,
    val notes: String? = null,
    val startDate: LocalDate? = null,
    val cancellation: Cancellation = Cancellation(),
    val bonus: Bonus = Bonus()
) {
    constructor(data: ContractDTO, userId: String) : this(
        userId,
        type = data.type,
        company = data.company,
        notes = data.notes,
        startDate = if (data.startDate?.isBlank() == false) LocalDate.parse(data.startDate) else null,
        cancellation = Cancellation.fromDTO(data),
        bonus = Bonus.fromDTO(data)
    )

    @Id
    lateinit var id: String

    fun getNextPossibleEndDate() =
        cancellation.getNextPossibleEndDate(startDate, LocalDate.now())?.format(
            DateTimeFormatter.ofLocalizedDate(
                FormatStyle.MEDIUM
            ).withLocale(LocaleContextHolder.getLocale())
        )
}

data class Cancellation(
    val minDuration: Period? = null,
    val noticePeriod: Period? = null,
    val extendPeriod: Period? = minDuration,
    val endDate: LocalDate? = null,
    val canceled: Boolean = endDate != null,
    val ack: Boolean = false
) {
    companion object {
        fun fromDTO(data: ContractDTO) = Cancellation(
            minDuration = if (data.minDurationUnit != null) Period.parse("P${data.minDurationValue?.toInt() ?: 0}${data.minDurationUnit}") else null,
            noticePeriod = if (data.noticePeriodUnit != null) Period.parse("P${data.noticePeriodValue?.toInt() ?: 0}${data.noticePeriodUnit}") else null,
            extendPeriod = if (data.extendPeriodUnit != null) Period.parse("P${data.extendPeriodValue?.toInt() ?: 0}${data.extendPeriodUnit}") else null,
            endDate = if (data.endDate?.isBlank() == false) LocalDate.parse(data.endDate) else null,
            canceled = data.canceled != null || data.endDate?.isBlank() == false,
            ack = data.cancelAck != null
        )
    }

    fun hasCancellationInfo() = minDuration != null || noticePeriod != null || canceled || ack || endDate != null

    /**
     * @param startDate the contract's start date, if known
     * @param now LocalDate.now() (parameterized for testing)
     * @return the next possible end date for the contract or null, if that information is not available
     */
    internal fun getNextPossibleEndDate(startDate: LocalDate?, now: LocalDate): LocalDate? {
        if (endDate != null) {
            return endDate
        }
        if (noticePeriod == null && (startDate == null || minDuration == null)) {
            return null
        }
        var possibleEnd = now
        if (noticePeriod != null) {
            possibleEnd = possibleEnd.plus(noticePeriod)
        }
        if (startDate != null && minDuration != null) {
            var nextTermEnd = startDate.plus(minDuration)
            while (nextTermEnd.isBefore(possibleEnd)) {
                nextTermEnd = nextTermEnd.plus(extendPeriod)
            }
            possibleEnd = nextTermEnd
        }
        return possibleEnd
    }

    /**
     * @param startDate the contract's start date, if known
     * @param now LocalDate.now() (parameterized for testing)
     * @return the latest possible date to cancel the contract as soon as possible, or null, if no notice period is set
     * or if the contract is already canceled
     */
    internal fun getLatestPossibleCancelDate(startDate: LocalDate?, now: LocalDate): LocalDate? {
        if (endDate != null || noticePeriod == null) {
            return null
        }
        return getNextPossibleEndDate(startDate, now)?.minus(noticePeriod)
    }
}

data class Bonus(val description: String? = null, val received: Boolean = false) {
    companion object {
        fun fromDTO(data: ContractDTO) = Bonus(
            description = if (data.bonusDescription?.isBlank() == false) data.bonusDescription else null,
            received = data.bonusReceived != null
        )
    }

    fun hasBonus() = description != null || received
}

data class ContractDTO(
    val type: String,
    val company: String,
    val notes: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val bonusDescription: String? = null,
    val bonusReceived: String? = null,
    val minDurationValue: String? = null,
    val minDurationUnit: Char? = null,
    val noticePeriodValue: String? = null,
    val noticePeriodUnit: Char? = null,
    val extendPeriodValue: String? = null,
    val extendPeriodUnit: Char? = null,
    val canceled: String? = null,
    val cancelAck: String? = null
)