package de.j4velin.contracts.user.data

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface UsersRepository : CrudRepository<User, String> {
    fun findByUsername(username: String): User?
}