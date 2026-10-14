package de.j4velin.contracts.controller

import de.j4velin.contracts.data.Cancellation
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

@Controller
class ContractsController(private val repository: ContractsRepository) {

    @GetMapping("/contracts")
    fun getContracts(model: Model, @AuthenticationPrincipal user: User): String {
        model.addAttribute(
            "contracts",
            Contract.sortedByDate(repository.findByUserId(user.requireId()))
        )
        return "contracts"
    }

    @GetMapping("/contracts/{id}")
    fun getContract(@PathVariable id: String, model: Model, @AuthenticationPrincipal user: User): String {
        val contract = getContract(id, user) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        model.addAttribute("contract", contract)
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
        getContract(id, user)?.also { repository.deleteById(id) } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return "redirect:/contracts"
    }

    @PostMapping("/contracts/{id}/toggleCancel")
    fun toggleCanceled(@PathVariable id: String, @AuthenticationPrincipal user: User): String {
        val update = getContract(id, user)?.let {
            Cancellation.toggleCancel(it).withId(id)
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        repository.save(update)
        return "redirect:/contracts"
    }

    @PostMapping("/contracts/{id}/toggleCancelAck")
    fun toggleCancelAck(@PathVariable id: String, @AuthenticationPrincipal user: User): String {
        val update = getContract(id, user)?.let {
            Cancellation.toggleCancelAck(it).withId(id)
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        repository.save(update)
        return "redirect:/contracts"
    }

    @PostMapping("/contracts/{id}/toggleBonus")
    fun toggleBonus(@PathVariable id: String, @AuthenticationPrincipal user: User): String {
        val update = getContract(id, user)?.let {
            val newBonus = it.bonus.copy(received = !it.bonus.received)
            it.copy(bonus = newBonus).withId(id)
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        repository.save(update)
        return "redirect:/contracts"
    }

    @PostMapping("/contracts/{id}")
    fun updateContract(
        @PathVariable id: String,
        contractDto: ContractDTO,
        @AuthenticationPrincipal user: User
    ): String {
        getContract(id, user) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val contract = Contract(validate(contractDto), user.requireId()).withId(id)
        repository.save(contract)
        return "redirect:/contracts"
    }

    /**
     * @param id the contract id to retrieve
     * @param user the current user
     * @return the requested contract, if such a contract exists and belongs to the given user. Contracts of other
     * users are treated like non-existing ones, so that their ids can't be probed
     */
    private fun getContract(id: String, user: User): Contract? =
        repository.findById(id)
            .filter { it.userId == user.requireId() }
            .map { it.withId(id) }
            .orElse(null)

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