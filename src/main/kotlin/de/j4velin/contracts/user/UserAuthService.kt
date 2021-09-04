package de.j4velin.contracts.user

import de.j4velin.contracts.user.data.UsersRepository
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Service

@Service
class UserAuthService(private val repository: UsersRepository) : UserDetailsService {

    override fun loadUserByUsername(username: String) =
        repository.findByUsername(username) ?: throw IllegalArgumentException("No such user: $username")

}