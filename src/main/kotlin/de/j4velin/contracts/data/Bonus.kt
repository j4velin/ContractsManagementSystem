package de.j4velin.contracts.data

/**
 * Data class representing a bonus / reward / cashback of a [Contract]
 *
 * @property description an optional description of this bonus
 * @property received true, if the bonus was received
 */
data class Bonus(val description: String? = null, val received: Boolean = false) {
    companion object {
        /**
         * Creates a [Bonus] from a [ContractDTO]
         * @param data the DTO data
         * @return the corresponding [Bonus] object
         */
        fun fromDTO(data: ContractDTO) = Bonus(
            description = if (data.bonusDescription?.isBlank() == false) data.bonusDescription else null,
            received = data.bonusReceived != null
        )
    }

    /**
     * @return true, if some bonus information is set
     */
    fun hasBonus() = description != null || received
}