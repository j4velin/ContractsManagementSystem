package de.j4velin.contracts.config

import de.j4velin.contracts.user.data.User
import de.j4velin.contracts.user.data.UsersRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

class UserLocaleResolverTest {

    private val usersRepository = mock(UsersRepository::class.java)
    private val resolver = UserLocaleResolver(usersRepository)
    private val germanUser =
        User(id = "u1", username = "hans", password = "secret", mail = "hans@example.com", locale = "de")

    private fun request() = MockHttpServletRequest().apply { addPreferredLocale(Locale.ENGLISH) }

    private fun login(user: User) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `anonymous user gets the Accept-Language locale`() {
        val request = request()
        assertEquals(Locale.ENGLISH, resolver.resolveLocale(request))
        assertEquals(Locale.ENGLISH, resolver.resolveLocaleContext(request).locale)
    }

    @Test
    fun `logged-in user gets the stored locale without a database query`() {
        login(germanUser)
        val request = request()
        assertEquals(Locale.GERMAN, resolver.resolveLocale(request))
        // the locale context is what LocaleContextHolder (and therefore date formatting) uses
        assertEquals(Locale.GERMAN, resolver.resolveLocaleContext(request).locale)
        verifyNoInteractions(usersRepository)
    }

    @Test
    fun `locale chosen in the session wins and is stored for the user`() {
        login(germanUser)
        given(usersRepository.findByUsername("hans")).willReturn(germanUser)
        val request = request()
        resolver.setLocale(request, MockHttpServletResponse(), Locale.ENGLISH)

        assertEquals(Locale.ENGLISH, resolver.resolveLocale(request))
        assertEquals(Locale.ENGLISH, resolver.resolveLocaleContext(request).locale)
        verify(usersRepository).save(germanUser.copy(locale = "en"))
    }
}
