package de.j4velin.contracts.user.data

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@Document
data class User(
    @Id val id: String,
    @Indexed(unique = true) private val username: String,
    private val password: String,
    val mail: String,
    val role: UserRole = UserRole.USER,
    val locale: String = "en"
) : UserDetails {
    override fun getAuthorities() = listOf(SimpleGrantedAuthority(role.name))
    override fun getPassword() = password
    override fun getUsername() = username
    override fun isAccountNonExpired() = true
    override fun isAccountNonLocked() = true
    override fun isCredentialsNonExpired() = true
    override fun isEnabled() = true

    /**
     * Constructs an [User] from an [UserDTO]
     * @param data the DTO data
     */
    constructor(data: UserDTO) : this(
        id = "", username = data.username, password = BCryptPasswordEncoder().encode(data.password), mail = data.mail
    )
}

enum class UserRole {
    USER
}

data class UserDTO(
    val username: String,
    val password: String,
    val mail: String
)
