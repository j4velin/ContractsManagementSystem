package de.j4velin.contracts.controller

import de.j4velin.contracts.data.Contract
import de.j4velin.contracts.data.ContractDTO
import de.j4velin.contracts.data.ContractsRepository
import de.j4velin.contracts.user.data.User
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate

@Controller
class ContractsController(private val repository: ContractsRepository, private val clock: Clock) {

    @GetMapping("/contracts")
    fun getContracts(model: Model, @AuthenticationPrincipal user: User): String {
        val today = today()
        model.addAttribute("contracts", Contract.sortedByDate(repository.findByUserId(user.requireId()), today))
        // the view shows whether a contract is still active and its next date relative to today
        model.addAttribute("today", today)
        return "contracts"
    }

    @GetMapping("/contracts/{id}")
    fun getContract(@PathVariable id: String, model: Model, @AuthenticationPrincipal user: User): String {
        model.addAttribute("contract", getOwnContract(id, user))
        return "contract"
    }

    @GetMapping("/contracts/add")
    fun getAddContracts(model: Model) = "contract"

    @PostMapping("/contracts")
    fun addContract(contractDto: ContractDTO, @AuthenticationPrincipal user: User): String {
        val contract = Contract(validate(contractDto), user.requireId())
        repository.save(contract)
        return "redirect:/contracts"
    }

    @PostMapping("/contracts/{id}/delete")
    fun deleteContract(@PathVariable id: String, @AuthenticationPrincipal user: User): String {
        getOwnContract(id, user)
        repository.deleteById(id)
        return "redirect:/contracts"
    }

    @PostMapping("/contracts/{id}/toggleCancel")
    fun toggleCanceled(@PathVariable id: String, @AuthenticationPrincipal user: User) =
        updateOwnContract(id, user) { it.toggleCancel(today()) }

    @PostMapping("/contracts/{id}/toggleCancelAck")
    fun toggleCancelAck(@PathVariable id: String, @AuthenticationPrincipal user: User) =
        updateOwnContract(id, user) { it.toggleCancelAck(today()) }

    @PostMapping("/contracts/{id}/toggleBonus")
    fun toggleBonus(@PathVariable id: String, @AuthenticationPrincipal user: User) =
        updateOwnContract(id, user) { it.toggleBonus() }

    @PostMapping("/contracts/{id}")
    fun updateContract(
        @PathVariable id: String,
        contractDto: ContractDTO,
        @AuthenticationPrincipal user: User
    ) = updateOwnContract(id, user) { Contract(validate(contractDto), user.requireId(), id) }

    /**
     * @return the current date of the injected [Clock]
     */
    private fun today(): LocalDate = LocalDate.now(clock)

    /**
     * @param id the contract id to retrieve
     * @param user the current user
     * @return the requested contract, if such a contract exists and belongs to the given user
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] otherwise. Contracts of other users are treated like
     * non-existing ones, so that their ids can't be probed
     */
    private fun getOwnContract(id: String, user: User): Contract =
        repository.findById(id)
            .filter { it.userId == user.requireId() }
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }

    /**
     * Loads one of the user's contracts, changes it and saves the result
     *
     * @param id the id of the contract to change
     * @param user the current user
     * @param change creates the changed contract from the stored one. Must keep its id, so that saving updates the
     * stored contract instead of inserting a new one
     * @return the redirect to the contracts list
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND], if the contract doesn't exist or belongs to another
     * user
     */
    private fun updateOwnContract(id: String, user: User, change: (Contract) -> Contract): String {
        repository.save(change(getOwnContract(id, user)))
        return "redirect:/contracts"
    }

    /**
     * Validates the required fields of a submitted contract
     *
     * @param contractDto the submitted contract
     * @return a copy of the DTO with trimmed type and company
     * @throws ResponseStatusException with [HttpStatus.BAD_REQUEST], if the type or the company is blank
     */
    private fun validate(contractDto: ContractDTO) = contractDto.copy(
        type = requireNotBlank(contractDto.type, "type"),
        company = requireNotBlank(contractDto.company, "company")
    )

    /**
     * @param value the raw form value
     * @param field the name of the form field, used in the error message
     * @return the trimmed value
     * @throws ResponseStatusException with [HttpStatus.BAD_REQUEST], if the value is blank
     */
    private fun requireNotBlank(value: String, field: String) =
        value.trim().ifEmpty { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing $field") }
}