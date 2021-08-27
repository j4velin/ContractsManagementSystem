package de.j4velin.contracts.data

import de.j4velin.contracts.localized
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

/**
 * Data class representing a contract / subscription
 *
 * @property userId the id of the user, who owns the contract
 * @property type describes what kind of contract this is (for example: Rent, ISP, Streaming subscription, ...)
 * @property company the other party of the contract
 * @property notes additional, user defined notes
 * @property startDate the date when this contract started
 * @property cancellation information on how to cancel the contract
 * @property bonus information about a bonus / reward program associated with the contract
 */
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
    /**
     * Constructs a [Contract] from a [ContractDTO]
     * @param data the DTO data
     * @param userId the userId of this contract's owner
     */
    constructor(data: ContractDTO, userId: String) : this(
        userId,
        type = data.type,
        company = data.company,
        notes = data.notes,
        startDate = if (data.startDate?.isBlank() == false) LocalDate.parse(data.startDate) else null,
        cancellation = Cancellation.fromDTO(data),
        bonus = Bonus.fromDTO(data)
    )

    /**
     * the contracts unique id. Assigned by the database
     */
    @Id
    lateinit var id: String

    /**
     * @return a localized string representation of the date of the next possible ending of the contract. Might be null
     * if that information is not available
     */
    fun getNextPossibleEndDate() =
        cancellation.getNextPossibleEndDate(startDate, LocalDate.now())?.localized()

    /**
     * @return a localized string representation of the latest date to cancel the contract in time. Might be null if
     * that information is not available
     */
    fun getLatestPossibleCancelDate() =
        cancellation.getLatestPossibleCancelDate(startDate, LocalDate.now())?.localized()

    /**
     * Sets the id on this contract
     *
     * @param id the id to set
     * @return this instance for chaining commands
     */
    fun withId(id: String): Contract {
        this.id = id
        return this
    }
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