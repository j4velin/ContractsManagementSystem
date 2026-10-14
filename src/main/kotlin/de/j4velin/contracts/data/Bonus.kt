package de.j4velin.contracts.data

/**
 * Data class representing a bonus / reward / cashback of a [Contract]
 *
 * @property description an optional description of this bonus
 * @property received true, if the bonus was received
 */
data class Bonus(val description: String? = null, val received: Boolean = false) {
    /**
     * @return true, if some bonus information is set
     */
    fun hasBonus() = description != null || received

    /**
     * @return a copy of this bonus with the [received] state toggled
     */
    internal fun toggleReceived() = copy(received = !received)
}