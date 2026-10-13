package de.j4velin.contracts.user

import de.j4velin.contracts.user.data.UsersRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.security.core.userdetails.UsernameNotFoundException

class UserAuthServiceTest {

    @Test
    fun `unknown user throws UsernameNotFoundException`() {
        // Spring Security only handles this exception type gracefully, e.g. for a remember-me cookie of a deleted user
        val service = UserAuthService(mock(UsersRepository::class.java))
        assertThrows<UsernameNotFoundException> { service.loadUserByUsername("nobody") }
    }
}
