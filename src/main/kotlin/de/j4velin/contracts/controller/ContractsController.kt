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

@Controller
class ContractsController(private val repository: ContractsRepository) {

    @GetMapping("/contracts")
    fun getContracts(model: Model, @AuthenticationPrincipal user: User): String {
        model.addAttribute(
            "contracts",
            repository.findByUserId(user.id)
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
        val contract = Contract(contractDto, user.id)
        repository.save(contract)
        return "redirect:/contracts"
    }

    @GetMapping("/contracts/{id}/delete")
    fun deleteContract(@PathVariable id: String, @AuthenticationPrincipal user: User): String {
        getContract(id, user)?.also { repository.deleteById(id) } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return "redirect:/contracts"
    }

    @GetMapping("/contracts/{id}/toggleCancel")
    fun toggleCanceled(@PathVariable id: String, @AuthenticationPrincipal user: User): String {
        val update = getContract(id, user)?.let {
            val newCancellation = it.cancellation.copy(canceled = !it.cancellation.canceled)
            it.copy(cancellation = newCancellation).run { this.id = id; this }
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        repository.save(update)
        return "redirect:/contracts"
    }

    @GetMapping("/contracts/{id}/toggleBonus")
    fun toggleBonus(@PathVariable id: String, @AuthenticationPrincipal user: User): String {
        val update = getContract(id, user)?.let {
            val newBonus = it.bonus.copy(received = !it.bonus.received)
            it.copy(bonus = newBonus).run { this.id = id; this }
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        println("toggleBonus $update, id=${update.id}")
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
        val contract = Contract(contractDto, user.id).also { it.id = id }
        repository.save(contract)
        return "redirect:/contracts"
    }

    private fun getContract(id: String, user: User): Contract? {
        val contract = repository.findById(id)
        return if (!contract.isPresent) {
            null
        } else if (contract.get().userId != user.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this item")
        } else {
            contract.get().let { it.id = id; it }
        }
    }
}