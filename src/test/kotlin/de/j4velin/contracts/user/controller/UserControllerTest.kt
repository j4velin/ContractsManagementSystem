package de.j4velin.contracts.user.controller

import de.j4velin.contracts.user.data.User
import de.j4velin.contracts.user.data.UsersRepository
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@WebMvcTest(UserController::class)
class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var repository: UsersRepository

    // required by WebSecurityConfig (remember-me)
    @MockBean
    private lateinit var userDetailsService: UserDetailsService

    private fun register(username: String = "alice", mail: String = "alice@example.com", password: String = "s3cret") =
        mockMvc.perform(
            post("/register").with(csrf())
                .param("username", username)
                .param("mail", mail)
                .param("password", password)
        )

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
}
