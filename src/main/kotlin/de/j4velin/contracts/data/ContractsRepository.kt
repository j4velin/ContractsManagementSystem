package de.j4velin.contracts.data

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ContractsRepository : CrudRepository<Contract, String> {
    fun findByUserId(userId: String): List<Contract>
}