package de.j4velin.contracts.data

import java.time.LocalDate
import java.time.Period

/**
 * Data class representing the cancellation data of a [Contract]
 *
 * @property minDuration the minimum duration, the contract must be active
 * @property noticePeriod the period of notice the contract requires to cancel
 * @property extendPeriod the period the contract is extended after the initial duration, if not canceled in time
 * @property endDate the date at which the contract ends
 * @property canceled true, if the contract is canceled
 * @property ack true, if the cancellation has been acknowledged by the other party
 */
data class Cancellation(
    val minDuration: Period? = null,
    val noticePeriod: Period? = null,
    val extendPeriod: Period? = minDuration,
    val endDate: LocalDate? = null,
    val canceled: Boolean = endDate != null,
    val ack: Boolean = false
) {
    companion object {
        /**
         * Creates a [Cancellation] from a [ContractDTO]
         * @param data the DTO data
         * @return the corresponding [Cancellation] object
         */
        fun fromDTO(data: ContractDTO) = Cancellation(
            minDuration = if (data.minDurationUnit != null) Period.parse("P${data.minDurationValue?.toInt() ?: 0}${data.minDurationUnit}") else null,
            noticePeriod = if (data.noticePeriodUnit != null) Period.parse("P${data.noticePeriodValue?.toInt() ?: 0}${data.noticePeriodUnit}") else null,
            extendPeriod = if (data.extendPeriodUnit != null) Period.parse("P${data.extendPeriodValue?.toInt() ?: 0}${data.extendPeriodUnit}") else null,
            endDate = if (data.endDate?.isBlank() == false) LocalDate.parse(data.endDate) else null,
            canceled = data.canceled != null || data.endDate?.isBlank() == false,
            ack = data.canceled != null && data.cancelAck != null
        )

        /**
         * Toggles the [Cancellation.canceled] state. Might also affect [Cancellation.endDate] and [Cancellation.ack]
         * properties
         *
         * @param contract the contract on which to toggle the cancel state
         * @param newAck the new cancel acknowledged value. Only considered, if this operation sets the state to 'canceled'
         * @return the modified [Contract]
         */
        fun toggleCancel(contract: Contract, newAck: Boolean = false): Contract {
            var endDate = contract.cancellation.endDate
            if (endDate == null && !contract.cancellation.canceled) {
                // -> now canceling -> set end date the same as the next possible end date
                endDate = contract.cancellation.getNextPossibleEndDate(contract.startDate, LocalDate.now())
            }
            // now canceling? -> set ack to parameter value. Now un-canceling? -> set ack to 'false'
            val newCancellation =
                contract.cancellation.copy(canceled = !contract.cancellation.canceled, endDate = endDate, ack = newAck)
            return contract.copy(cancellation = newCancellation)
        }

        /**
         * Toggles the [Cancellation.ack] state. Might also affect [Cancellation.endDate] and [Cancellation.canceled]
         * properties
         *
         * @param contract the contract on which to toggle the cancel ack state
         * @return the modified [Contract]
         */
        fun toggleCancelAck(contract: Contract): Contract {
            return if (!contract.cancellation.canceled && !contract.cancellation.ack) {
                // not canceled and not acknowledged? -> toggle both
                toggleCancel(contract, true)
            } else {
                contract.copy(cancellation = contract.cancellation.copy(ack = !contract.cancellation.ack))
            }
        }
    }

    /**
     * @return true, if any cancellation information is set
     */
    fun hasCancellationInfo() = minDuration != null || noticePeriod != null || canceled || endDate != null

    /**
     * @param startDate the contract's start date, if known
     * @param now LocalDate.now() (parameterized for testing)
     * @return the next possible end date for the contract or null, if that information is not available
     */
    internal fun getNextPossibleEndDate(startDate: LocalDate?, now: LocalDate = LocalDate.now()): LocalDate? {
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
    internal fun getLatestPossibleCancelDate(startDate: LocalDate?, now: LocalDate = LocalDate.now()): LocalDate? {
        if (endDate != null || noticePeriod == null) {
            return null
        }
        return getNextPossibleEndDate(startDate, now)?.minus(noticePeriod)
    }
}