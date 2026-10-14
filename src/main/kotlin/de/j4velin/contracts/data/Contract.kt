package de.j4velin.contracts.data

import de.j4velin.contracts.localized
import de.j4velin.contracts.parseDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
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
@ConsistentCopyVisibility
data class Contract internal constructor(
    // every list view queries by the owner (ContractsRepository.findByUserId)
    @field:Indexed
    val userId: String,
    val type: String,
    val company: String,
    val notes: String? = null,
    val startDate: LocalDate? = null,
    val cancellation: Cancellation = Cancellation(),
    val bonus: Bonus = Bonus()
) : Comparable<Contract> {

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
        startDate = parseDate(data.startDate, "startDate"),
        cancellation = Cancellation.fromDTO(data),
        bonus = Bonus.fromDTO(data)
    )

    /**
     * the contracts unique id. Assigned by the database
     */
    @Id
    lateinit var id: String

    /**
     * @return a localized string representation of the next possible ending or the latest date to cancel the contract or
     * null, if no such information is available
     */
    fun getLocalizedEndDate() = getSortDate()?.localized()

    /**
     * @return true, if this contract is considered to be 'active', e.g. if it is not yet canceled, the end date is in
     * the future or if it has outstanding bonus rewards
     */
    fun isActive() =
        !cancellation.canceled || (bonus.hasBonus() && !bonus.received) || cancellation.endDate?.isAfter(LocalDate.now()) ?: true

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

    /**
     * Compares by [getSortDate]. Calculates both dates on every call: to sort many contracts, prefer [sortedByDate],
     * which calculates them only once per contract
     */
    override fun compareTo(other: Contract) = SORT_DATE_ORDER.compare(getSortDate(), other.getSortDate())

    /**
     * @param now LocalDate.now() (parameterized for testing)
     * @return the latest date to cancel the contract in time or, if no notice period is set or the contract is already
     * canceled, the date of the next possible ending. Null, if neither information is available
     */
    internal fun getSortDate(now: LocalDate = LocalDate.now()) =
        cancellation.getLatestPossibleCancelDate(startDate, now) ?: cancellation.getNextPossibleEndDate(startDate, now)

    companion object {
        /**
         * Earlier dates first, contracts without a date last
         */
        private val SORT_DATE_ORDER = nullsLast(naturalOrder<LocalDate>())

        /**
         * Sorts the contracts the same way as their natural order ([compareTo]), but calculates each contract's
         * [getSortDate] only once
         *
         * @param contracts the contracts to sort
         * @param now LocalDate.now() (parameterized for testing)
         * @return the sorted contracts
         */
        internal fun sortedByDate(contracts: Iterable<Contract>, now: LocalDate = LocalDate.now()): List<Contract> =
            contracts.map { it to it.getSortDate(now) }
                .sortedWith(compareBy(SORT_DATE_ORDER) { it.second })
                .map { it.first }
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