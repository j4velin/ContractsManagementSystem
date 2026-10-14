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
 * @property id the MongoDB document id. Must be `null` for a contract which has not been persisted yet, so that Spring
 * Data treats it as new and MongoDB generates a unique id on insert. Part of the constructor, so that [copy] keeps it
 * and saving a modified copy updates the contract instead of inserting a new one
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
    val bonus: Bonus = Bonus(),
    @Id val id: String? = null
) {

    /**
     * Constructs a [Contract] from a [ContractDTO]
     * @param data the DTO data
     * @param userId the userId of this contract's owner
     * @param id the id of the contract, if the DTO updates an existing contract. Null for a new contract
     */
    constructor(data: ContractDTO, userId: String, id: String? = null) : this(
        userId,
        type = data.type,
        company = data.company,
        notes = data.notes,
        startDate = parseDate(data.startDate, "startDate"),
        cancellation = Cancellation.fromDTO(data),
        bonus = Bonus.fromDTO(data),
        id = id
    )

    /**
     * @param today the current date
     * @return a localized string representation of the next possible ending or the latest date to cancel the contract or
     * null, if no such information is available
     */
    fun getLocalizedEndDate(today: LocalDate) = getSortDate(today)?.localized()

    /**
     * @param today the current date
     * @return true, if this contract is considered to be 'active', e.g. if it is not yet canceled, the end date is in
     * the future or if it has outstanding bonus rewards
     */
    fun isActive(today: LocalDate) =
        !cancellation.canceled || (bonus.hasBonus() && !bonus.received) || cancellation.endDate?.isAfter(today) ?: true

    /**
     * Toggles the canceled state, see [Cancellation.toggleCancel]
     *
     * @param today the current date
     * @return the modified copy of this contract
     */
    fun toggleCancel(today: LocalDate) = copy(cancellation = cancellation.toggleCancel(startDate, today))

    /**
     * Toggles the cancellation acknowledged state, see [Cancellation.toggleCancelAck]
     *
     * @param today the current date
     * @return the modified copy of this contract
     */
    fun toggleCancelAck(today: LocalDate) = copy(cancellation = cancellation.toggleCancelAck(startDate, today))

    /**
     * Toggles whether the bonus was received, see [Bonus.toggleReceived]
     *
     * @return the modified copy of this contract
     */
    fun toggleBonus() = copy(bonus = bonus.toggleReceived())

    /**
     * @param today the current date
     * @return the latest date to cancel the contract in time or, if no notice period is set or the contract is already
     * canceled, the date of the next possible ending. Null, if neither information is available
     */
    internal fun getSortDate(today: LocalDate) =
        cancellation.getLatestPossibleCancelDate(startDate, today) ?: cancellation.getNextPossibleEndDate(startDate, today)

    companion object {
        /**
         * Earlier dates first, contracts without a date last
         */
        private val SORT_DATE_ORDER = nullsLast(naturalOrder<LocalDate>())

        /**
         * Compares contracts by [getSortDate]. Calculates both dates on every comparison: to sort many contracts,
         * prefer [sortedByDate], which calculates them only once per contract
         *
         * @param today the current date
         * @return the comparator
         */
        internal fun sortDateComparator(today: LocalDate): Comparator<Contract> =
            Comparator { a, b -> SORT_DATE_ORDER.compare(a.getSortDate(today), b.getSortDate(today)) }

        /**
         * Sorts the contracts the same way as [sortDateComparator], but calculates each contract's [getSortDate] only
         * once
         *
         * @param contracts the contracts to sort
         * @param today the current date
         * @return the sorted contracts
         */
        internal fun sortedByDate(contracts: Iterable<Contract>, today: LocalDate): List<Contract> =
            contracts.map { it to it.getSortDate(today) }
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