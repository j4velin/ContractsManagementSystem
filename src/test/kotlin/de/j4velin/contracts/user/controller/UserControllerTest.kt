package de.j4velin.contracts.user.controller

import de.j4velin.contracts.testsupport.ContractsWebMvcTest
import de.j4velin.contracts.user.data.User
import de.j4velin.contracts.user.data.UsersRepository
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@ContractsWebMvcTest(controllers = [UserController::class])
class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    // the mock from @ContractsWebMvcTest
    @Autowired
    private lateinit var repository: UsersRepository

    // the mock from @ContractsWebMvcTest
    @Autowired
    private lateinit var userDetailsService: UserDetailsService

    // the bean from WebSecurityConfig, which is also used for the login
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    // the real bean from WebSecurityConfig, spied on to see which repository the controller replaces the token in
    @MockitoSpyBean
    private lateinit var csrfTokenRepository: CsrfTokenRepository

    private fun registerRequest(username: String = "alice", mail: String = "alice@example.com", password: String = "s3cret") =
        post("/register").with(csrf())
            .param("username", username)
            .param("mail", mail)
            .param("password", password)

    /**
     * A registration within the given session, with a real CSRF token instead of the one [csrf] provides, which
     * bypasses the CSRF token repository
     */
    private fun registerRequest(session: MockHttpSession, csrfToken: String) =
        post("/register").session(session)
            .param("_csrf", csrfToken)
            .param("username", "alice")
            .param("mail", "alice@example.com")
            .param("password", "s3cret")

    /**
     * Loads the registration form like a browser and returns the CSRF token it contains
     */
    private fun fetchCsrfToken(session: MockHttpSession): String {
        val result = mockMvc.perform(get("/register").session(session)).andReturn()
        val token = result.request.getAttribute(CsrfToken::class.java.name) as CsrfToken
        return token.token
    }

    private fun register(username: String = "alice", mail: String = "alice@example.com", password: String = "s3cret") =
        mockMvc.perform(registerRequest(username, mail, password))

    /**
     * Lets the repository mock store the saved user, so that the login after the registration finds it
     */
    private fun saveUsers() {
        var saved: User? = null
        given(repository.save(any(User::class.java))).willAnswer { invocation ->
            invocation.getArgument<User>(0).also { saved = it }
        }
        given(userDetailsService.loadUserByUsername("alice")).willAnswer { saved }
    }

    private fun MockHttpServletRequestBuilder.asLoggedInUser() = with(user("bob"))

    @Test
    fun `registration saves the user with the encoded password and logs in`() {
        saveUsers()
        register()
            .andExpect(redirectedUrl("/"))
            .andExpect(authenticated().withUsername("alice"))
        val captor = ArgumentCaptor.forClass(User::class.java)
        verify(repository).save(captor.capture())
        val user = captor.value
        assertEquals("alice", user.username)
        assertEquals("alice@example.com", user.mail)
        assertNotEquals("s3cret", user.password)
        assertTrue(passwordEncoder.matches("s3cret", user.password))
    }

    @Test
    fun `registration changes the session id and replaces the CSRF token`() {
        saveUsers()
        val session = MockHttpSession()
        val sessionIdBefore = session.id
        // a real token stored in the session: the one from csrf() never gets there
        val token = fetchCsrfToken(session)
        assertNotNull(session.getAttribute(CSRF_TOKEN_ATTRIBUTE))
        mockMvc.perform(registerRequest(session, token))
            .andExpect(redirectedUrl("/"))
            .andExpect(authenticated().withUsername("alice"))
        assertNotEquals(sessionIdBefore, session.id)
        // the CSRF token used for the registration is removed, the next form gets a new one
        assertNull(session.getAttribute(CSRF_TOKEN_ATTRIBUTE))
    }

    @Test
    fun `registration replaces the CSRF token in the configured repository`() {
        saveUsers()
        val session = MockHttpSession()
        val token = fetchCsrfToken(session)
        clearInvocations(csrfTokenRepository)
        mockMvc.perform(registerRequest(session, token)).andExpect(redirectedUrl("/"))
        // saving null removes the token; only the session authentication strategy of the controller does this here
        verify(csrfTokenRepository).saveToken(isNull(), any(), any())
    }

    @Test
    fun `CSRF token from before the registration is rejected afterwards, a new one works`() {
        saveUsers()
        val session = MockHttpSession()
        val oldToken = fetchCsrfToken(session)
        mockMvc.perform(registerRequest(session, oldToken)).andExpect(redirectedUrl("/"))

        mockMvc.perform(registerRequest(session, oldToken)).andExpect(status().isForbidden)
        // logged in now, so another registration passes the CSRF check, but is redirected
        mockMvc.perform(registerRequest(session, fetchCsrfToken(session))).andExpect(redirectedUrl("/contracts"))
        verify(repository).save(any())
    }

    @Test
    fun `registration form is shown to anonymous users`() {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk)
            .andExpect(view().name("register"))
    }

    @Test
    fun `logged in users are redirected from the registration form`() {
        mockMvc.perform(get("/register").asLoggedInUser())
            .andExpect(redirectedUrl("/contracts"))
    }

    @Test
    fun `logged in users can't register another user`() {
        mockMvc.perform(registerRequest().asLoggedInUser())
            .andExpect(redirectedUrl("/contracts"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `logged in users are redirected from a registration without any form fields`() {
        mockMvc.perform(post("/register").with(csrf()).asLoggedInUser())
            .andExpect(redirectedUrl("/contracts"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `missing mail is rejected like a blank one without saving`() {
        mockMvc.perform(
            post("/register").with(csrf())
                .param("username", "alice")
                .param("password", "s3cret")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("register"))
            .andExpect(model().attribute("error", "register.error.missingFields"))
            .andExpect(model().attribute("username", "alice"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `missing password is rejected like a blank one without saving`() {
        mockMvc.perform(
            post("/register").with(csrf())
                .param("username", "alice")
                .param("mail", "alice@example.com")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("register"))
            .andExpect(model().attribute("error", "register.error.missingFields"))
            .andExpect(model().attribute("username", "alice"))
            .andExpect(model().attribute("mail", "alice@example.com"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `blank fields are rejected without saving`() {
        register(username = "  ", password = "")
            .andExpect(status().isOk)
            .andExpect(view().name("register"))
            .andExpect(model().attribute("error", "register.error.missingFields"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `existing username is rejected without saving`() {
        given(repository.findByUsername("alice"))
            .willReturn(User(id = "u1", username = "alice", password = "x", mail = "a@example.com"))
        register(username = " alice ")
            .andExpect(view().name("register"))
            .andExpect(model().attribute("error", "register.error.userExists"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `duplicate key from the unique index shows the error message`() {
        given(repository.save(any(User::class.java))).willThrow(DuplicateKeyException("duplicate"))
        register()
            .andExpect(view().name("register"))
            .andExpect(model().attribute("error", "register.error.userExists"))
    }

    @Test
    fun `form is refilled with username and mail but not the password`() {
        register(username = "bob", mail = "bob@example.com", password = "")
            .andExpect(content().string(containsString("value=\"bob\"")))
            .andExpect(content().string(containsString("value=\"bob@example.com\"")))
            .andExpect(model().attributeDoesNotExist("data"))
            .andExpect(content().string(not(containsString("s3cret"))))
    }

    @Test
    fun `error message is localized`() {
        mockMvc.perform(
            post("/register").with(csrf())
                .param("locale", "de")
                .param("username", "")
                .param("mail", "")
                .param("password", "")
        ).andExpect(content().string(containsString("Bitte alle Felder ausfüllen")))
    }

    private companion object {
        val CSRF_TOKEN_ATTRIBUTE = HttpSessionCsrfTokenRepository::class.java.name + ".CSRF_TOKEN"
    }
}
