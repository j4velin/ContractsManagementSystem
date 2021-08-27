package de.j4velin.contracts.user.data

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

@Document
data class User(
    @Indexed(unique = true) private val username: String,
    private val password: String,
    val mail: String,
    val role: UserRole = UserRole.USER
) : UserDetails {
    @Id
    lateinit var id: String

    override fun getAuthorities() = listOf(SimpleGrantedAuthority(role.name))
    override fun getPassword() = password
    override fun getUsername() = username
    override fun isAccountNonExpired() = true
    override fun isAccountNonLocked() = true
    override fun isCredentialsNonExpired() = true
    override fun isEnabled() = true
}

enum class UserRole {
    USER
}
